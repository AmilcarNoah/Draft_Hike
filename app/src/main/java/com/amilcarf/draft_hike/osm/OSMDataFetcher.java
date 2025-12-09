package com.amilcarf.draft_hike.osm;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.amilcarf.draft_hike.models.OSMNode;
import com.amilcarf.draft_hike.models.OSMWay;
import com.amilcarf.draft_hike.models.Trail;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OSMDataFetcher {
    private static final String TAG = "OSMDataFetcher";
    private static final String OSM_API_URL = "https://overpass-api.de/api/interpreter";
    private static final String CACHE_FILENAME = "trails_cache.json";
    private static final long CACHE_SIZE = 10 * 1024 * 1024; // 10 MB cache

    private final OkHttpClient client;
    private final Context context;
    private final Gson gson;

    public OSMDataFetcher(Context context) {
        this.context = context;
        this.gson = new Gson();

        // Create cache directory;
        File cacheDir = new File(context.getCacheDir(), "osm_cache");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        // Create HTTP client with caching
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .cache(new Cache(cacheDir, CACHE_SIZE))
                .build();
    }

    public List<OSMWay> fetchTrailsNearLocation(double lat, double lon, double radius) throws IOException, JSONException {
        // Fixed query
        String query = String.format(Locale.US,
                "[out:json][timeout:15];" +
                        "(" +
                        "  way[\"highway\"=\"path\"][\"foot\"!=\"no\"](around:%.0f,%.6f,%.6f);" +
                        "  way[\"route\"=\"hiking\"](around:%.0f,%.6f,%.6f);" +
                        ");" +
                        "out body;" +
                        ">;" +
                        "out skel qt;",
                radius, lat, lon,
                radius, lat, lon
        );

        Log.d(TAG, "Fetching trails query (radius=" + radius + "m)");
        String jsonResponse = executeOverpassQuery(query);
        return parseWaysFromJson(jsonResponse);
    }

    public List<OSMNode> fetchBenchesNearLocation(double lat, double lon, double radius) throws IOException, JSONException {
        // Fixed query - removed invalid "limit" statement
        String query = String.format(Locale.US,
                "[out:json][timeout:10];" +
                        "node[\"amenity\"=\"bench\"](around:%.0f,%.6f,%.6f);" +
                        "out body;",
                radius, lat, lon
        );

        Log.d(TAG, "Fetching benches query (radius=" + radius + "m)");
        String jsonResponse = executeOverpassQuery(query);
        return parseNodesFromJson(jsonResponse);
    }

    private String executeOverpassQuery(String query) throws IOException {
        try {
            // Encode the query!!!!
            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            String url = OSM_API_URL + "?data=" + encodedQuery;

            Log.d(TAG, "Executing query to Overpass API");
            Log.d(TAG, "Query preview: " + query.substring(0, Math.min(100, query.length())));

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "DraftHikeApp/1.0")
                    .build();

            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                Log.e(TAG, "HTTP error " + response.code() + ": " + errorBody);
                throw new IOException("HTTP " + response.code() + ": " + errorBody);
            }

            String responseBody = response.body().string();
            Log.d(TAG, "Response received, length: " + responseBody.length() + " bytes");

            return responseBody;

        } catch (Exception e) {
            Log.e(TAG, "Error in executeOverpassQuery: " + e.getMessage(), e);
            throw new IOException("Failed to execute Overpass query: " + e.getMessage(), e);
        }
    }

    private List<OSMWay> parseWaysFromJson(String jsonResponse) throws JSONException {
        List<OSMWay> ways = new ArrayList<>();
        Map<Long, OSMNode> nodeMap = new HashMap<>();

        JSONObject json = new JSONObject(jsonResponse);
        JSONArray elements = json.getJSONArray("elements");

        Log.d(TAG, "Total elements in response: " + elements.length());

        // First pass!!!: collect only nodes that belong to ways
        for (int i = 0; i < elements.length(); i++) {
            JSONObject element = elements.getJSONObject(i);
            if ("node".equals(element.getString("type"))) {
                long id = element.getLong("id");
                double lat = element.getDouble("lat");
                double lon = element.getDouble("lon");

                OSMNode node = new OSMNode(id, lat, lon);
                nodeMap.put(id, node);
            }
        }

        Log.d(TAG, "Collected " + nodeMap.size() + " nodes");

        // Second pass!: process ways
        int wayCount = 0;
        for (int i = 0; i < elements.length(); i++) {
            JSONObject element = elements.getJSONObject(i);
            if ("way".equals(element.getString("type"))) {
                wayCount++;
                long id = element.getLong("id");
                OSMWay way = new OSMWay(id);

                // Get nodes for this way
                JSONArray nodesArray = element.getJSONArray("nodes");
                List<OSMNode> wayNodes = new ArrayList<>();

                for (int j = 0; j < nodesArray.length(); j++) {
                    long nodeId = nodesArray.getLong(j);
                    OSMNode node = nodeMap.get(nodeId);
                    if (node != null) {
                        wayNodes.add(node);
                    }
                }

                // Skip ways with too few nodes (likely not a real trail)
                if (wayNodes.size() < 2) {
                    Log.d(TAG, "Skipping way " + id + " - only " + wayNodes.size() + " nodes");
                    continue;
                }

                // Add nodes to way
                for (OSMNode node : wayNodes) {
                    way.addNode(node);
                }

                // Get tags
                if (element.has("tags")) {
                    JSONObject tags = element.getJSONObject("tags");
                    Iterator<String> keys = tags.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        String value = tags.getString(key);
                        way.addTag(key, value);
                    }
                }

                ways.add(way);

                // Limit to 30 ways maximum
                if (ways.size() >= 30) {
                    break;
                }
            }
        }

        Log.d(TAG, "Processed " + wayCount + " ways, kept " + ways.size() + " valid ways");
        return ways;
    }

    private List<OSMNode> parseNodesFromJson(String jsonResponse) throws JSONException {
        List<OSMNode> nodes = new ArrayList<>();

        JSONObject json = new JSONObject(jsonResponse);
        JSONArray elements = json.getJSONArray("elements");

        for (int i = 0; i < elements.length(); i++) {
            JSONObject element = elements.getJSONObject(i);
            if ("node".equals(element.getString("type"))) {
                long id = element.getLong("id");
                double lat = element.getDouble("lat");
                double lon = element.getDouble("lon");

                OSMNode node = new OSMNode(id, lat, lon);

                // Get tags
                if (element.has("tags")) {
                    JSONObject tags = element.getJSONObject("tags");
                    Iterator<String> keys = tags.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        String value = tags.getString(key);
                        node.addTag(key, value);
                    }
                }

                nodes.add(node);
            }
        }

        Log.d(TAG, "Parsed " + nodes.size() + " benches from JSON");
        return nodes;
    }

    public void cacheTrails(List<Trail> trails) {
        try {
            String json = gson.toJson(trails);

            File cacheFile = new File(context.getFilesDir(), CACHE_FILENAME);
            FileOutputStream fos = new FileOutputStream(cacheFile);
            fos.write(json.getBytes());
            fos.close();

            // Save cache timestamp
            SharedPreferences prefs = context.getSharedPreferences("TrailsPrefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong("cache_timestamp", System.currentTimeMillis());
            editor.apply();

            Log.d(TAG, "Cached " + trails.size() + " trails");
        } catch (IOException e) {
            Log.e(TAG, "Failed to cache trails", e);
        }
    }

    public List<Trail> loadCachedTrails() {
        try {
            File cacheFile = new File(context.getFilesDir(), CACHE_FILENAME);
            if (!cacheFile.exists()) {
                return null;
            }

            FileInputStream fis = new FileInputStream(cacheFile);
            byte[] data = new byte[(int) cacheFile.length()];
            fis.read(data);
            fis.close();

            String json = new String(data);
            Type listType = new TypeToken<List<Trail>>(){}.getType();
            return gson.fromJson(json, listType);

        } catch (IOException e) {
            Log.e(TAG, "Failed to load cached trails", e);
            return null;
        }
    }

    public String getLastCacheTime() {
        SharedPreferences prefs = context.getSharedPreferences("TrailsPrefs", Context.MODE_PRIVATE);
        long timestamp = prefs.getLong("cache_timestamp", 0);

        if (timestamp == 0) {
            return "Never";
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}