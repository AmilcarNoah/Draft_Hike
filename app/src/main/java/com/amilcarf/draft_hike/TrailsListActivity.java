package com.amilcarf.draft_hike;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amilcarf.draft_hike.adapters.TrailAdapter;
import com.amilcarf.draft_hike.models.OSMNode;
import com.amilcarf.draft_hike.models.OSMWay;
import com.amilcarf.draft_hike.models.Trail;
import com.amilcarf.draft_hike.osm.OSMDataFetcher;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrailsListActivity extends AppCompatActivity {

    private static final String TAG = "TrailsListActivity";
    private static final String PREFS_NAME = "TrailsPrefs";
    private static final String LAST_FETCH_TIME = "last_fetch_time";
    private static final long CACHE_DURATION = 30 * 60 * 1000; // 30 minutes- for efficiency rather than loading everytime

    // Location permissions
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private RecyclerView recyclerViewTrails;
    private TrailAdapter trailAdapter;
    private List<Trail> allTrails;
    private List<Trail> filteredTrails;
    private EditText searchEditText;
    private ImageView clearSearchBtn;
    private ImageView locationButton;
    private ProgressBar loadingProgressBar;
    private View emptyStateLayout;
    private TextView emptyStateText;
    private TextView emptyStateSubtext;
    private Button retryButton;
    private Toolbar toolbar;
    private OSMDataFetcher osmDataFetcher;
    private ExecutorService executorService;

    // Location variables
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location currentLocation;
    private boolean locationPermissionGranted = false;
    private boolean isFetchingLocation = false;

    // Default location (used as fallback)---Necessary at the moment!!!!
    private double defaultLatitude = 51.02; // Dresden
    private double defaultLongitude = 13.72;


    private double searchRadius = 500; // 500m radius

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trails_list);

        // Initialize views
        toolbar = findViewById(R.id.toolbar);
        searchEditText = findViewById(R.id.searchEditText);
        clearSearchBtn = findViewById(R.id.clearSearchBtn);
        locationButton = findViewById(R.id.locationButton);
        recyclerViewTrails = findViewById(R.id.recyclerViewTrails);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        emptyStateText = findViewById(R.id.emptyStateText);
        emptyStateSubtext = findViewById(R.id.emptyStateSubtext);
        retryButton = findViewById(R.id.retryButton);

        // Setup toolbar
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Nearby Trails");
        }

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        // Initialize OSM data fetcher
        osmDataFetcher = new OSMDataFetcher(this);

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Create thread pool
        executorService = Executors.newFixedThreadPool(2);

        // RecyclerView here
        setupRecyclerView();

        // Search functionality
        setupSearch();

        // Setup location button click listener
        locationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                useCurrentLocation();
            }
        });

        // FOr retry button click listener
        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                useCurrentLocation();
            }
        });

        // loading animation
        loadingProgressBar.setVisibility(View.VISIBLE);
        recyclerViewTrails.setVisibility(View.GONE);
        emptyStateLayout.setVisibility(View.GONE);

        // loading screen messages
        emptyStateText.setText("Finding trails near you...");
        emptyStateSubtext.setText("Searching for hiking trails...");

        // Check location permission and load data
        checkLocationPermissionAndLoad();
    }

    private void setupRecyclerView() {
        allTrails = new ArrayList<>();
        filteredTrails = new ArrayList<>();

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerViewTrails.setLayoutManager(layoutManager);

        trailAdapter = new TrailAdapter(filteredTrails,
                new TrailAdapter.OnItemClickListener() {
                    @Override
                    public void onItemClick(Trail trail) {
                        openTrailDetails(trail);
                    }
                },
                new TrailAdapter.OnFavoriteClickListener() {
                    @Override
                    public void onFavoriteClick(Trail trail, int position) {
                        toggleFavorite(trail, position);
                    }
                },
                new TrailAdapter.OnStartTrailClickListener() {
                    @Override
                    public void onStartTrailClick(Trail trail) {
                        startTrailNavigation(trail);
                    }
                });

        recyclerViewTrails.setAdapter(trailAdapter);
    }
    //maybe this is not necessary
    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTrails(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
                clearSearchBtn.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
        });

        clearSearchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchEditText.setText("");
            }
        });

        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            filterTrails(searchEditText.getText().toString());
            return true;
        });
    }

    private void checkLocationPermissionAndLoad() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true;
            useCurrentLocation();
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Show explanation
            showToast("Location permission is needed to find trails near you");
            requestLocationPermission();
        } else {
            requestLocationPermission();
        }
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationPermissionGranted = true;
                useCurrentLocation();
            } else {
                locationPermissionGranted = false;
                showToast("Location permission denied. Using sample trails.");
//                loadSampleData();
            }
        }
    }

    private void useCurrentLocation() {
        if (isFetchingLocation) {
            return;
        }

        if (!locationPermissionGranted) {
            checkLocationPermissionAndLoad();
            return;
        }

        isFetchingLocation = true;
        loadingProgressBar.setVisibility(View.VISIBLE);
        emptyStateText.setText("Getting your location...");
        emptyStateSubtext.setText("Searching for trails nearby");

        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            isFetchingLocation = false;

                            if (location != null) {
                                currentLocation = location;
                                double lat = location.getLatitude();
                                double lon = location.getLongitude();

                                showToast("Found your location! Loading nearby trails...");
                                loadTrailsWithLocation(lat, lon);
                            } else {
                                // Last location is null, try to get current location
                                requestCurrentLocation();
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        isFetchingLocation = false;
                        Log.e(TAG, "Failed to get last location", e);
                        showToast("Could not get location. Using sample trails.");
//                        loadSampleData();
                    });

        } catch (SecurityException e) {
            isFetchingLocation = false;
            Log.e(TAG, "SecurityException when requesting location", e);
            showToast("Location permission required.");
//            loadSampleData();
        }
    }

    private void requestCurrentLocation() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setNumUpdates(1);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                isFetchingLocation = false;

                if (locationResult == null) {
                    showToast("Could not get current location. Using sample trails.");
//                    loadSampleData();
                    return;
                }

                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        currentLocation = location;
                        double lat = location.getLatitude();
                        double lon = location.getLongitude();

                        showToast("Found your location! Loading nearby trails...");
                        loadTrailsWithLocation(lat, lon);

                        // Remove location updates to save battery--- Recommendation !!!!
                        fusedLocationClient.removeLocationUpdates(locationCallback);
                        return;
                    }
                }

                showToast("Could not get current location. Using sample trails.");
//                loadSampleData();
            }
        };

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest,
                    locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            isFetchingLocation = false;
            Log.e(TAG, "SecurityException when requesting location updates", e);
            showToast("Location permission required.");
//            loadSampleData();
        }
    }
    // requires more testing; concern with the loading of the locations...
    private void loadTrailsWithLocation(double latitude, double longitude) {
        Log.d(TAG, "Loading trails for location: " + latitude + ", " + longitude);

        executorService.execute(() -> {
            try {
                // Fetch trails from OSM
                List<OSMWay> osmTrails = osmDataFetcher.fetchTrailsNearLocation(
                        latitude, longitude, searchRadius);

                Log.d(TAG, "Fetched " + (osmTrails != null ? osmTrails.size() : 0) + " trails from OSM");

                // Fetch benches
                List<OSMNode> osmBenches = osmDataFetcher.fetchBenchesNearLocation(
                        latitude, longitude, searchRadius);

                Log.d(TAG, "Fetched " + (osmBenches != null ? osmBenches.size() : 0) + " benches from OSM");

                // Process the data
                List<Trail> trails = new ArrayList<>();
                if (osmTrails != null && !osmTrails.isEmpty()) {
                    trails = processOSMDataToTrails(osmTrails, osmBenches, latitude, longitude);
                }

                if (trails.isEmpty()) {
                    // Increase search radius and try again; Recommendation !!!!
                    double largerRadius = searchRadius * 2;
                    showToast("No trails found within " + (searchRadius/500) + "meters. Searching " + (largerRadius/1000) + "km...");

                    osmTrails = osmDataFetcher.fetchTrailsNearLocation(latitude, longitude, largerRadius);
                    if (osmTrails != null && !osmTrails.isEmpty()) {
                        trails = processOSMDataToTrails(osmTrails, osmBenches, latitude, longitude);
                    }
                }

                final List<Trail> finalTrails = trails;
                new Handler(Looper.getMainLooper()).post(() -> {
                    loadingProgressBar.setVisibility(View.GONE);

                    if (finalTrails.isEmpty()) {
//                        loadSampleData();
                        showToast("No trails found nearby.");
                    } else {
                        allTrails.clear();
                        allTrails.addAll(finalTrails);

                        filteredTrails.clear();
                        filteredTrails.addAll(allTrails);
                        trailAdapter.updateData(filteredTrails);

                        recyclerViewTrails.setVisibility(View.VISIBLE);
                        emptyStateLayout.setVisibility(View.GONE);

                        showToast("Found " + finalTrails.size() + " trails near you!");
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading trails from OSM", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    loadingProgressBar.setVisibility(View.GONE);
//                    loadSampleData();
                    showToast("Cannot connect to trail database. Using sample trails.");
                });
            }
        });
    }

    private List<Trail> processOSMDataToTrails(List<OSMWay> osmTrails, List<OSMNode> osmBenches,
                                               double userLat, double userLon) {
        List<Trail> trails = new ArrayList<>();

        for (int i = 0; i < Math.min(osmTrails.size(), 10); i++) {
            OSMWay osmWay = osmTrails.get(i);

            try {
                // Calculate trail length
                double distanceKm = calculateTrailLength(osmWay);
                if (distanceKm < 0.1) continue;

                // Estimate duration
                int durationMinutes = (int) ((distanceKm / 5.0) * 60);
                if (durationMinutes < 1) durationMinutes = 5;
                String duration = durationMinutes + " min";

                // COunt benches
                int benchCount = 0;
                if (osmBenches != null) {
                    benchCount = countBenchesNearTrail(osmWay, osmBenches, 100); // 100m radius reasonable for context
                }

                // Get trail name
                String name = osmWay.getTag("name");
                if (name == null || name.isEmpty()) {
                    name = "Trail " + (i + 1);
                }

                // Get difficulty
                String difficulty = getDifficultyFromTags(osmWay);
                String status = "Open";
                String description = generateTrailDescription(osmWay);

                // Trail geom
                List<OSMNode> wayNodes = osmWay.getNodes();
                List<LatLng> coordinates = new ArrayList<>();
                double startLat = 0, startLng = 0, endLat = 0, endLng = 0;

                if (wayNodes != null && !wayNodes.isEmpty()) {
                    // Convert OSMNodes to LatLng
                    for (OSMNode node : wayNodes) {
                        LatLng latLng = new LatLng(node.getLatitude(), node.getLongitude());
                        coordinates.add(latLng);
                    }

                    // Set start and end points
                    startLat = wayNodes.get(0).getLatitude();
                    startLng = wayNodes.get(0).getLongitude();
                    endLat = wayNodes.get(wayNodes.size() - 1).getLatitude();
                    endLng = wayNodes.get(wayNodes.size() - 1).getLongitude();
                }

                // trail geo
                Trail trail = new Trail(
                        String.valueOf(osmWay.getId()),
                        name,
                        Math.round(distanceKm * 10.0) / 10.0,
                        duration,
                        benchCount,  //bench count
                        difficulty,
                        status,
                        description,
                        false,  // isFavorite
                        coordinates,
                        null,   // polyline (can encode later if needed)
                        startLat, startLng,
                        endLat, endLng
                );

                trails.add(trail);

            } catch (Exception e) {
                Log.w(TAG, "Error processing trail", e);
            }
        }

        return trails;
    }
    //need mod based on segment rather than nodes....
    private int countBenchesNearTrail(OSMWay trail, List<OSMNode> allBenches, double radiusMeters) {
        if (allBenches == null || allBenches.isEmpty()) {
            return 0;
        }

        int benchCount = 0;
        List<OSMNode> trailNodes = trail.getNodes();

        if (trailNodes == null || trailNodes.isEmpty()) {
            return 0;
        }

        // Check each bench against each trail node
        for (OSMNode bench : allBenches) {
            double benchLat = bench.getLatitude();
            double benchLon = bench.getLongitude();

            // Check if bench is near any point on the trail
            for (OSMNode trailNode : trailNodes) {
                double trailLat = trailNode.getLatitude();
                double trailLon = trailNode.getLongitude();

                float[] results = new float[1];
                Location.distanceBetween(
                        benchLat, benchLon,
                        trailLat, trailLon,
                        results
                );

                if (results[0] <= radiusMeters) {
                    benchCount++;
                    break; // Count this bench only once
                }
            }
        }

        return benchCount;
    }

//    private void loadSampleData() {
//        allTrails.clear();
//
//        // Sample coordinates for Central Park Loop
//        List<LatLng> centralParkCoords = Arrays.asList(
//                new LatLng(40.7829, -73.9654),
//                new LatLng(40.7828, -73.9660),
//                new LatLng(40.7820, -73.9665),
//                new LatLng(40.7815, -73.9670),
//                new LatLng(40.7829, -73.9654) // loop back
//        );
//
//        // Create sample trail WITH geometry
//        Trail centralPark = new Trail(
//                "1",
//                "Central Park Loop",
//                6.1,
//                "2 hours",
//                12,
//                "Easy",
//                "Open",
//                "Scenic loop around Central Park with multiple resting benches and water fountains.",
//                false,
//                centralParkCoords,
//                null,
//                40.7829, -73.9654,  // start
//                40.7829, -73.9654   // end (loop)
//        );
//
//        allTrails.add(centralPark);
//
//        // Add other sample trails similarly...
//
//        filteredTrails.clear();
//        filteredTrails.addAll(allTrails);
//        trailAdapter.updateData(filteredTrails);
//
//        loadingProgressBar.setVisibility(View.GONE);
//        recyclerViewTrails.setVisibility(View.VISIBLE);
//        emptyStateLayout.setVisibility(View.GONE);
//    }

    private double calculateTrailLength(OSMWay way) {
        List<OSMNode> nodes = way.getNodes();
        if (nodes == null || nodes.size() < 2) {
            return 1.0;
        }

        // Simple distance calculation
        double totalDistance = 0.0;
        for (int i = 0; i < nodes.size() - 1; i++) {
            OSMNode node1 = nodes.get(i);
            OSMNode node2 = nodes.get(i + 1);

            double lat1 = node1.getLatitude();
            double lon1 = node1.getLongitude();
            double lat2 = node2.getLatitude();
            double lon2 = node2.getLongitude();

            double dLat = Math.toRadians(lat2 - lat1);
            double dLon = Math.toRadians(lon2 - lon1);
            double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                            Math.sin(dLon/2) * Math.sin(dLon/2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

            totalDistance += 6371000 * c; // Earth's radius in meters
        }

        return totalDistance / 1000; // Convert to km
    }

    private String getDifficultyFromTags(OSMWay way) {
        String difficulty = way.getTag("trail:difficulty");
        if (difficulty != null) {
            return difficulty;
        }

        String surface = way.getTag("surface");
        if ("paved".equals(surface) || "asphalt".equals(surface)) {
            return "Easy";
        } else if ("gravel".equals(surface) || "compacted".equals(surface)) {
            return "Medium";
        }

        return "Medium";
    }

    private String generateTrailDescription(OSMWay way) {
        StringBuilder description = new StringBuilder();

        String name = way.getTag("name");
        if (name != null) {
            description.append(name).append(". ");
        }

        String surface = way.getTag("surface");
        if (surface != null) {
            description.append("Surface: ").append(surface).append(". ");
        }

        if (description.length() == 0) {
            description.append("A scenic hiking trail suitable for outdoor enthusiasts.");
        }

        return description.toString();
    }

    private void filterTrails(String query) {
        filteredTrails.clear();

        if (query.isEmpty()) {
            filteredTrails.addAll(allTrails);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (Trail trail : allTrails) {
                if (trail.getName().toLowerCase().contains(lowerCaseQuery) ||
                        trail.getDescription().toLowerCase().contains(lowerCaseQuery) ||
                        trail.getDifficulty().toLowerCase().contains(lowerCaseQuery)) {
                    filteredTrails.add(trail);
                }
            }
        }

        trailAdapter.updateData(filteredTrails);
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (filteredTrails.isEmpty()) {
            emptyStateText.setText("No trails found");
            emptyStateSubtext.setText("Try searching with different keywords");
            retryButton.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.VISIBLE);
            recyclerViewTrails.setVisibility(View.GONE);
        } else {
            emptyStateLayout.setVisibility(View.GONE);
            recyclerViewTrails.setVisibility(View.VISIBLE);
        }
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(TrailsListActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    private void openTrailDetails(Trail trail) {
        Intent intent = new Intent(this, TrailDetailActivity.class);
        intent.putExtra("trail_id", trail.getId());
        intent.putExtra("trail_name", trail.getName());
        intent.putExtra("trail_distance", trail.getDistance());
        intent.putExtra("trail_duration", trail.getDuration());
        intent.putExtra("trail_benches", trail.getBenchCount());
        intent.putExtra("trail_difficulty", trail.getDifficulty());
        intent.putExtra("trail_description", trail.getDescription());
        startActivity(intent);
    }

    private void toggleFavorite(Trail trail, int position) {
        trail.setFavorite(!trail.isFavorite());
        trailAdapter.updateItem(position, trail);

        for (int i = 0; i < allTrails.size(); i++) {
            if (allTrails.get(i).getId().equals(trail.getId())) {
                allTrails.set(i, trail);
                break;
            }
        }
    }

    private void startTrailNavigation(Trail trail) {
        Intent intent = new Intent(this, NavigationActivity.class);
        intent.putExtra("trail", trail); // Pass the entire trail object
        intent.putExtra("from_trail_list", true);

        // Pass user location if available
        if (currentLocation != null) {
            intent.putExtra("user_lat", currentLocation.getLatitude());
            intent.putExtra("user_lon", currentLocation.getLongitude());
        }

        startActivity(intent);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Remove location updates
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        // Shutdown executor
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}