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
import java.util.Arrays;
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

    // Multiple Overpass API endpoints for failover
    private static final String[] OVERPASS_ENDPOINTS = {
            "https://overpass-api.de/api/interpreter",
            "https://overpass.openstreetmap.fr/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
    };

    private static final String CACHE_FILENAME = "trails_cache.json";
    private static final long CACHE_SIZE = 10 * 1024 * 1024; // 10 MB cache
    private static final int MAX_RETRIES_PER_ENDPOINT = 2;
    private static final int MAX_TOTAL_TRIES = 6; // Total across all endpoints

    private final OkHttpClient client;
    private final Context context;
    private final Gson gson;

    public OSMDataFetcher(Context context) {
        this.context = context;
        this.gson = new Gson();

        // cache directory
        File cacheDir = new File(context.getCacheDir(), "osm_cache");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        // HTTP client
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)  // Increased from 10
                .readTimeout(45, TimeUnit.SECONDS)     // Increased from 15
                .writeTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)        // Enable retry on connection failure
                .cache(new Cache(cacheDir, CACHE_SIZE))
                .build();
    }

    // To find benches along trail
    public List<OSMNode> fetchBenchesInBoundingBox(double south, double west, double north, double east)
            throws IOException, JSONException {

        // Format: (south,west,north,east) - Overpass API expects this order
        String query = String.format(Locale.US,
                "[out:json][timeout:25];" +
                        "node[\"amenity\"=\"bench\"](%.6f,%.6f,%.6f,%.6f);" +
                        "out body;",
                south, west, north, east
        );

        Log.d(TAG, "Fetching benches in bounding box: " +
                south + "," + west + " to " + north + "," + east);

        String jsonResponse = executeOverpassQueryWithRetry(query);
        return parseNodesFromJson(jsonResponse);
    }

    public List<OSMWay> fetchTrailsNearLocation(double lat, double lon, double radius) throws IOException, JSONException {
        // multiple query strategies- Redundant; but fallback*
        List<String> queries = Arrays.asList(
                // Original query
                String.format(Locale.US,
                        "[out:json][timeout:25];" +  // Increased timeout
                                "(" +
                                "  way[\"highway\"=\"path\"][\"foot\"!=\"no\"](around:%.0f,%.6f,%.6f);" +
                                "  way[\"route\"=\"hiking\"](around:%.0f,%.6f,%.6f);" +
                                ");" +
                                "out body;" +
                                ">;" +
                                "out skel qt;",
                        Math.min(radius, 1000), lat, lon,  // Reduced radius to 1000m max
                        Math.min(radius, 1000), lat, lon
                ),

                // Simplified query 1: Just hiking routes
                String.format(Locale.US,
                        "[out:json][timeout:20];" +
                                "way[\"route\"=\"hiking\"](around:%.0f,%.6f,%.6f);" +
                                "out body;" +
                                ">;" +
                                "out skel qt;",
                        Math.min(radius, 1500), lat, lon
                ),

                // Simplified query 2: Just paths
                String.format(Locale.US,
                        "[out:json][timeout:20];" +
                                "way[\"highway\"=\"path\"](around:%.0f,%.6f,%.6f);" +
                                "out body;" +
                                ">;" +
                                "out skel qt;",
                        Math.min(radius, 1500), lat, lon
                )
        );

        Log.d(TAG, "Trying to fetch trails (radius=" + radius + "m)");

        for (String query : queries) {
            try {
                String jsonResponse = executeOverpassQueryWithRetry(query);
                List<OSMWay> ways = parseWaysFromJson(jsonResponse);
                if (!ways.isEmpty()) {
                    Log.d(TAG, "Successfully fetched " + ways.size() + " ways with query strategy");
                    return ways;
                }
            } catch (Exception e) {
                Log.w(TAG, "Query strategy failed: " + e.getMessage());
            }
        }

        throw new IOException("All query strategies failed for trail fetch");
    }

    public List<OSMNode> fetchBenchesNearLocation(double lat, double lon, double radius) throws IOException, JSONException {
        String query = String.format(Locale.US,
                "[out:json][timeout:15];" +
                        "node[\"amenity\"=\"bench\"](around:%.0f,%.6f,%.6f);" +
                        "out body;",
                Math.min(radius, 1000), lat, lon  // Reduced radius-- 1km atm
        );

        Log.d(TAG, "Fetching benches query (radius=" + radius + "m)");
        String jsonResponse = executeOverpassQueryWithRetry(query);
        return parseNodesFromJson(jsonResponse);
    }

    private String executeOverpassQueryWithRetry(String query) throws IOException {
        Exception lastException = null;
        int totalTries = 0;

        // Try each endpoint with retries
        for (String endpoint : OVERPASS_ENDPOINTS) {
            for (int retry = 0; retry < MAX_RETRIES_PER_ENDPOINT; retry++) {
                totalTries++;

                if (totalTries > MAX_TOTAL_TRIES) {
                    throw new IOException("Max total tries (" + MAX_TOTAL_TRIES + ") exceeded", lastException);
                }

                try {
                    // Exponential backoff between retries
                    if (retry > 0) {
                        long waitTime = (long) Math.pow(2, retry) * 1000; // 2^retry seconds
                        Thread.sleep(waitTime);
                    }

                    Log.d(TAG, "Attempt " + totalTries + " on endpoint: " + endpoint + " (retry " + retry + ")");
                    return executeSingleQuery(endpoint, query);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Thread interrupted", e);
                } catch (Exception e) {
                    lastException = e;
                    Log.w(TAG, "Attempt " + totalTries + " failed: " + e.getMessage());

                    // If it's a timeout or server error, try next endpoint
                    if (e.getMessage() != null &&
                            (e.getMessage().contains("timeout") ||
                                    e.getMessage().contains("504") ||
                                    e.getMessage().contains("Gateway Timeout"))) {
                        break; // Try next endpoint
                    }
                }
            }
        }

        throw new IOException("All endpoints failed after " + totalTries + " attempts", lastException);
    }

    private String executeSingleQuery(String endpoint, String query) throws IOException {
        try {
            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            String url = endpoint + "?data=" + encodedQuery;

            Log.d(TAG, "Executing query to: " + endpoint);
            Log.d(TAG, "Query length: " + query.length() + " chars");

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "DraftHikeApp/1.0 (https://github.com/yourusername/DraftHike)")
                    .header("Accept", "application/json")
                    .build();

            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                Log.e(TAG, "HTTP error " + response.code() + " from " + endpoint);

                if (response.code() == 504 || response.code() == 503) {
                    throw new IOException("Server timeout/unavailable (" + response.code() + ") from " + endpoint);
                }

                throw new IOException("HTTP " + response.code() + " from " + endpoint + ": " +
                        errorBody.substring(0, Math.min(200, errorBody.length())));
            }

            String responseBody = response.body().string();

            // Check for Overpass error in response
            if (responseBody.contains("<strong style=\"color:#FF0000\">Error</strong>")) {
                throw new IOException("Overpass API error in response");
            }

            Log.d(TAG, "Success from " + endpoint + ", response length: " + responseBody.length() + " bytes");
            return responseBody;

        } catch (Exception e) {
            Log.e(TAG, "Error executing query on " + endpoint + ": " + e.getMessage());
            throw new IOException("Failed on endpoint " + endpoint + ": " + e.getMessage(), e);
        }
    }

    private List<OSMWay> parseWaysFromJson(String jsonResponse) throws JSONException {
        List<OSMWay> ways = new ArrayList<>();
        Map<Long, OSMNode> nodeMap = new HashMap<>();

        JSONObject json = new JSONObject(jsonResponse);
        JSONArray elements = json.getJSONArray("elements");

        Log.d(TAG, "Total elements in response: " + elements.length());

        // First pass: collect only nodes that belong to ways
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

        // Second pass: process ways
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
                if (wayNodes.size() < 3) {
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

                    // Add name if available
                    if (tags.has("name")) {
                        way.addTag("name", tags.getString("name"));
                    }
                }

                ways.add(way);

                // Limit to 20 ways maximum to reduce processing
                if (ways.size() >= 20) {
                    Log.d(TAG, "Reached max ways limit (20)");
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

                // Limit benches to 50
                if (nodes.size() >= 50) {
                    break;
                }
            }
        }

        Log.d(TAG, "Parsed " + nodes.size() + " benches from JSON");
        return nodes;
    }

    // Quick fallback method for when OSM is down
    public List<OSMWay> getFallbackTrails(double lat, double lon) {
        Log.d(TAG, "Using fallback trail generation");
        List<OSMWay> fallbackWays = new ArrayList<>();

        // Create 3 simple circular trails around the location
        for (int i = 0; i < 3; i++) {
            OSMWay way = new OSMWay(1000000L + i);

            // Create circular path
            int points = 20;
            double radius = 0.002 * (i + 1); // Different sizes

            for (int j = 0; j <= points; j++) {
                double angle = 2 * Math.PI * j / points;
                double pointLat = lat + radius * Math.cos(angle);
                double pointLon = lon + radius * Math.sin(angle);

                OSMNode node = new OSMNode(2000000L + i * 100 + j, pointLat, pointLon);
                way.addNode(node);
            }

            // Add some metadata
            way.addTag("name", "Sample Trail " + (i + 1));
            way.addTag("highway", "path");
            way.addTag("foot", "yes");

            fallbackWays.add(way);
        }

        return fallbackWays;
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

    // Method to check if cache is still valid (less than 1 hour old)
    public boolean isCacheValid() {
        SharedPreferences prefs = context.getSharedPreferences("TrailsPrefs", Context.MODE_PRIVATE);
        long timestamp = prefs.getLong("cache_timestamp", 0);

        if (timestamp == 0) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long oneHour = 60 * 60 * 1000; // 1 hour in milliseconds

        return (currentTime - timestamp) < oneHour;
    }
}