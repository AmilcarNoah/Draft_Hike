package com.amilcarf.draft_hike;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "MapActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int LOCATION_TIMEOUT_MS = 10000;
    private static final int NETWORK_TIMEOUT_MS = 15000;
    private static final float DEFAULT_ZOOM_LEVEL = 15f;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private Location currentLocation;
    private boolean showBenches;
    private LocationCallback locationCallback;
    private final AtomicInteger locationFetchAttempts = new AtomicInteger(0);  // REFERENCE 1
    private volatile boolean isFetchingLocation = false;  // REFERENCE 2
    private volatile boolean isMapReady = false;  // REFERENCE 2
    private FetchBenchesTask currentFetchTask;
    private final Executor singleThreadExecutor = Executors.newSingleThreadExecutor();  // REFERENCE 3

    // Cache for bench data to avoid repeated network calls;makes it faster
    private static class LocationCache {
        private static LatLng cachedLocation;
        private static List<Bench> cachedBenches;
        private static long cacheTimestamp;
        private static final long CACHE_DURATION_MS = 300000; // cache duration for 5 min

        static boolean isValid(LatLng location) {
            return cachedLocation != null
                    && cachedBenches != null
                    && System.currentTimeMillis() - cacheTimestamp < CACHE_DURATION_MS  // time validity check
                    && distanceBetween(cachedLocation, location) < 50; // spatial validity check with 50m radius
        }

        static void cache(LatLng location, List<Bench> benches) {
            cachedLocation = location;
            cachedBenches = benches;
            cacheTimestamp = System.currentTimeMillis();
        }

        static List<Bench> getCachedBenches() {
            return cachedBenches;
        }

        // Calc using Haversine
        private static double distanceBetween(LatLng loc1, LatLng loc2) {
            float[] results = new float[1];
            Location.distanceBetween(loc1.latitude, loc1.longitude,
                    loc2.latitude, loc2.longitude, results);
            return results[0];
        }
    }

    // Overpass API URL template for benches
    private static final String OVERPASS_API_URL =
            "https://overpass-api.de/api/interpreter?data=[out:json];" +
                    "node[\"amenity\"=\"bench\"]" +
                    "(around:1000,%f,%f);" +
                    "out body;";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // Check if we should show benches
        Bundle extras = getIntent().getExtras();
        showBenches = extras != null && extras.getBoolean("show_benches", false);

        // Initialize FusedLocationProviderClient - Google Dev
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        setupToolbar();  // Toolbar for return
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // REFERENCE 10: Lambda expression (Java 8+) *****************
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // REFERENCE 11: Lifecycle-aware initialization
        if (isMapReady && showBenches && currentLocation == null && checkLocationPermission()) {
            enableMyLocation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        removeLocationUpdates();  // REFERENCE 12: Battery optimization
        cancelPendingTasks();  // Resource cleanup for performance
    }

    //Prevent memory leaks/battery drain (Android Developer Docs)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeLocationUpdates();
        cancelPendingTasks();
        mMap = null;
        fusedLocationClient = null;
    }

    private void removeLocationUpdates() {
        // REFERENCE 15: FusedLocationProviderClient best practice
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;  // Allow GC to collect
        }
    }

    private void cancelPendingTasks() {
        // REFERENCE 16: AsyncTask lifecycle management
        if (currentFetchTask != null && !currentFetchTask.isCancelled()) {
            currentFetchTask.cancel(true);  // REFERENCE 17: Interrupt thread
            currentFetchTask = null;
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        isMapReady = true;  // REFERENCE 18: Memory barrier (happens-before)

        configureMap(googleMap);

        if (checkLocationPermission()) {
            enableMyLocation();
        } else {
            requestLocationPermission();
        }
    }

    private void configureMap(GoogleMap googleMap) {
        try {
            mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style));
        } catch (Exception e) {
            Log.e(TAG, "Error loading map style", e);
        }

        UiSettings uiSettings = googleMap.getUiSettings();
        uiSettings.setCompassEnabled(true);
        uiSettings.setMyLocationButtonEnabled(true);
        uiSettings.setMapToolbarEnabled(true);
        uiSettings.setZoomControlsEnabled(true);

        // REFERENCE 19: Minimal listener pattern
        mMap.setOnMapClickListener(latLng -> {
            // Optional: Add functionality here
        });
    }

    private boolean checkLocationPermission() {
        // REFERENCE 20: ContextCompat vs ActivityCompat (more efficient)
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
            } else {
                showToast("Location permission denied. Cannot show benches.");
            }
        }
    }

    private void enableMyLocation() {
        // REFERENCE 21: Check permission before expensive operation
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            fetchCurrentLocation();
        }
    }

    private void fetchCurrentLocation() {
        if (isFetchingLocation) return;  // REFERENCE 22: Debouncing pattern

        isFetchingLocation = true;
        locationFetchAttempts.set(0);  // REFERENCE 23: Atomic reset

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            isFetchingLocation = false;
            return;
        }

        fetchLocationWithRetry();
    }

    private void fetchLocationWithRetry() {
        // REFERENCE 24: Exponential backoff strategy
        if (locationFetchAttempts.get() >= MAX_RETRY_ATTEMPTS) {
            isFetchingLocation = false;
            showToast("Unable to get location after multiple attempts");
            return;
        }

        locationFetchAttempts.incrementAndGet();  // REFERENCE 25: Thread-safe increment

        Task<Location> lastLocationTask = fusedLocationClient.getLastLocation();
        lastLocationTask.addOnCompleteListener(this, task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                handleLocationSuccess(task.getResult());
            } else {
                requestFreshLocationUpdate();  // REFERENCE 26: Fallback strategy
            }
        });
    }

    private void requestFreshLocationUpdate() {
        try {
            LocationRequest locationRequest = createLocationRequest();

            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult != null) {
                        Location location = locationResult.getLastLocation();
                        if (location != null) {
                            handleLocationSuccess(location);
                        } else {
                            retryLocationFetch();
                        }
                    } else {
                        retryLocationFetch();
                    }
                }
            };

            // REFERENCE 27: LocationRequest optimization (PRIORITY_HIGH_ACCURACY for quick fix)
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
            setupLocationTimeout();  // REFERENCE 28: Timeout pattern

        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when requesting location", e);
            isFetchingLocation = false;
        }
    }

    private LocationRequest createLocationRequest() {
        // REFERENCE 29: Google's LocationRequest best practices
        return LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10000)
                .setFastestInterval(5000)
                .setNumUpdates(1);  // REFERENCE 30: Single update for battery
    }

    private void setupLocationTimeout() {
        // REFERENCE 31: ExecutorService for background timeout (vs Handler)
        singleThreadExecutor.execute(() -> {
            try {
                Thread.sleep(LOCATION_TIMEOUT_MS);
                runOnUiThread(() -> {
                    if (isFetchingLocation) {
                        retryLocationFetch();
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();  // REFERENCE 32: Proper interruption handling
            }
        });
    }

    private void handleLocationSuccess(Location location) {
        isFetchingLocation = false;  // REFERENCE 33: Memory visibility (happens-before)
        removeLocationUpdates();  // REFERENCE 34: Stop unnecessary updates

        currentLocation = location;
        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());

        updateMapLocation(currentLatLng);
        addCurrentLocationMarker(currentLatLng);

        if (showBenches) {
            findNearbyBenches(currentLatLng);
        }
    }

    private void retryLocationFetch() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        if (locationFetchAttempts.get() < MAX_RETRY_ATTEMPTS) {
            fetchLocationWithRetry();
        } else {
            isFetchingLocation = false;
            showToast("Unable to get current location");
        }
    }

    private void updateMapLocation(LatLng latLng) {
        if (mMap != null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM_LEVEL));
        }
    }

    private void addCurrentLocationMarker(LatLng latLng) {
        if (mMap != null) {
            mMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title("Your Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
        }
    }

    private void findNearbyBenches(LatLng currentLatLng) {
        // REFERENCE 35: Cache-first strategy (Network Performance Optimization)
        if (LocationCache.isValid(currentLatLng)) {
            displayBenches(LocationCache.getCachedBenches());  // Sub-millisecond response
            return;
        }

        // Clear map and re-add current location
        if (mMap != null) {
            mMap.clear();
            addCurrentLocationMarker(currentLatLng);
        }

        // Start async task to fetch benches
        cancelPendingTasks();
        currentFetchTask = new FetchBenchesTask(currentLatLng);
        // REFERENCE 36: THREAD_POOL_EXECUTOR vs SERIAL_EXECUTOR (parallel execution)
        currentFetchTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                currentLocation.getLatitude(),
                currentLocation.getLongitude()
        );
    }

    private void displayBenches(List<Bench> benches) {
        if (benches == null || benches.isEmpty()) {
            showToast("No benches found within 1km radius");
            return;
        }

        if (mMap == null) return;

        // REFERENCE 37: Batch marker addition (vs incremental)
        for (Bench bench : benches) {
            LatLng benchLocation = new LatLng(bench.getLatitude(), bench.getLongitude());
            mMap.addMarker(new MarkerOptions()
                    .position(benchLocation)
                    .title("Bench")
                    .snippet(bench.getName())
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_bench_vector)));
        }

        showToast("Found " + benches.size() + " benches within 1km");
    }

    private void showToast(String message) {
        // REFERENCE 38: Thread-safe UI updates
        runOnUiThread(() -> Toast.makeText(MapActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    // REFERENCE 39: Immutable data class pattern
    private static class Bench {
        private final double latitude;  // REFERENCE 40: Final fields (thread-safe)
        private final double longitude;
        private final String name;

        public Bench(double latitude, double longitude, String name) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.name = name;
        }

        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public String getName() { return name; }
    }

    private class FetchBenchesTask extends AsyncTask<Double, Void, List<Bench>> {
        private final LatLng requestLocation;  // REFERENCE 41: Local context for caching

        FetchBenchesTask(LatLng requestLocation) {
            this.requestLocation = requestLocation;
        }

        @Override
        protected List<Bench> doInBackground(Double... params) {
            if (isCancelled()) return null;  // REFERENCE 42: Early cancellation check

            double latitude = params[0];
            double longitude = params[1];

            return fetchBenchesWithRetry(latitude, longitude, 0);
        }

        private List<Bench> fetchBenchesWithRetry(double lat, double lon, int attempt) {
            // REFERENCE 43: Circuit breaker pattern (max attempts)
            if (attempt >= 2 || isCancelled()) {
                return new ArrayList<>();
            }

            HttpURLConnection connection = null;
            try {
                String urlString = String.format(OVERPASS_API_URL, lat, lon);
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                configureConnection(connection);

                // REFERENCE 44: HTTP status code validation
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    String jsonResponse = readStream(connection.getInputStream());
                    List<Bench> benches = parseBenchesFromJSON(jsonResponse);

                    // Cache the results (Write-through cache)
                    if (!benches.isEmpty()) {
                        LocationCache.cache(requestLocation, benches);
                    }

                    return benches;
                } else {
                    Log.e(TAG, "HTTP error code: " + connection.getResponseCode());
                    return fetchBenchesWithRetry(lat, lon, attempt + 1);  // Retry on failure
                }

            } catch (IOException | JSONException e) {
                Log.e(TAG, "Error fetching benches (attempt " + (attempt + 1) + ")", e);
                return fetchBenchesWithRetry(lat, lon, attempt + 1);
            } finally {
                // REFERENCE 45: Guaranteed resource cleanup (try-finally pattern)
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        private void configureConnection(HttpURLConnection connection) throws IOException {
            // REFERENCE 46: Connection pooling optimization
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(NETWORK_TIMEOUT_MS);
            connection.setReadTimeout(NETWORK_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "DraftHikeApp/1.0");  // REFERENCE 47: API etiquette
        }

        // REFERENCE 48: Buffered I/O optimization
        private String readStream(InputStream inputStream) throws IOException {
            StringBuilder result = new StringBuilder(8192);  // REFERENCE 49: Pre-sized StringBuilder
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                char[] buffer = new char[4096];  // REFERENCE 50: 4KB buffer (optimal for Android)
                int charsRead;
                while ((charsRead = reader.read(buffer)) != -1) {
                    if (isCancelled()) break;  // REFERENCE 51: Check cancellation during I/O
                    result.append(buffer, 0, charsRead);
                }
            }
            return result.toString();
        }

        private List<Bench> parseBenchesFromJSON(String jsonString) throws JSONException {
            List<Bench> benches = new ArrayList<>();
            JSONObject json = new JSONObject(jsonString);
            JSONArray elements = json.getJSONArray("elements");

            // REFERENCE 52: Early exit on cancellation
            for (int i = 0; i < elements.length() && !isCancelled(); i++) {
                JSONObject element = elements.getJSONObject(i);

                if ("node".equals(element.getString("type"))) {
                    double lat = element.getDouble("lat");
                    double lon = element.getDouble("lon");
                    String benchName = extractBenchName(element);
                    benches.add(new Bench(lat, lon, benchName));
                }
            }

            return benches;
        }

        private String extractBenchName(JSONObject element) throws JSONException {
            if (!element.has("tags")) return "Bench";

            JSONObject tags = element.getJSONObject("tags");
            if (tags.has("name")) {
                String name = tags.getString("name");
                if (!name.isEmpty()) return name;  // REFERENCE 53: String validation
            }

            if (tags.has("backrest") && "yes".equals(tags.getString("backrest"))) {
                return "Bench (with backrest)";
            }

            return "Bench";
        }

        @Override
        protected void onPostExecute(List<Bench> benches) {
            super.onPostExecute(benches);
            if (benches != null && !isCancelled()) {
                displayBenches(benches);
            }
            currentFetchTask = null;  // REFERENCE 54: Clear reference to allow GC
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            currentFetchTask = null;  // REFERENCE 55: Cleanup on cancellation
        }
    }
}