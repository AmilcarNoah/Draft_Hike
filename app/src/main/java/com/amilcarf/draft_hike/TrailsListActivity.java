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
import androidx.appcompat.app.AlertDialog;
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
import com.amilcarf.draft_hike.location.LocationManager;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrailsListActivity extends AppCompatActivity
        implements LocationManager.LocationListener {

    private static final String TAG = "TrailsListActivity";
    private static final String PREFS_NAME = "TrailsPrefs";
    private static final String LAST_FETCH_TIME = "last_fetch_time";
    private static final long CACHE_DURATION = 30 * 60 * 1000; // 30 minutes

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

    // LocationManager instance; Java Class from Location Manager
    private LocationManager locationManager;

    // Default location (used as fallback)
    private double defaultLatitude = 51.02; // Dresden
    private double defaultLongitude = 13.72;

    private double searchRadius = 500; // 500m radius

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trails_list);

        // Initialize LocationManager
        locationManager = LocationManager.getInstance(this);
        locationManager.addLocationListener(this);

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

        // For retry button click listener
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
        if (locationManager.hasLocationPermissions()) {
            // Check if we have a fresh location already
            if (locationManager.isLocationFresh()) {
                Location location = locationManager.getCurrentLocation();
                if (location != null) {
                    loadTrailsWithLocation(location.getLatitude(), location.getLongitude());
                } else {
                    getCurrentLocation();
                }
            } else {
                getCurrentLocation();
            }
        } else {
            // Request permission
            requestLocationPermission();
        }
    }

    private void requestLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Show explanation dialog
            new AlertDialog.Builder(this)
                    .setTitle("Location Permission")
                    .setMessage("This app needs location access to find hiking trails near you.")
                    .setPositiveButton("OK", (dialog, which) -> {
                        locationManager.requestPermissions(this, LOCATION_PERMISSION_REQUEST_CODE);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        showToast("Location permission denied. Using default location.");
                        loadTrailsWithLocation(defaultLatitude, defaultLongitude);
                    })
                    .create()
                    .show();
        } else {
            locationManager.requestPermissions(this, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, get location
                getCurrentLocation();
            } else {
                showToast("Location permission denied. Using default location.");
                loadTrailsWithLocation(defaultLatitude, defaultLongitude);
            }
        }
    }

    private void getCurrentLocation() {
        loadingProgressBar.setVisibility(View.VISIBLE);
        emptyStateText.setText("Getting your location...");
        emptyStateSubtext.setText("Searching for trails nearby");

        locationManager.getSingleLocation(this);
    }

    private void useCurrentLocation() {
        getCurrentLocation();
    }

    // LocationManager.LocationListener implementation
    @Override
    public void onLocationReceived(Location location) {
        runOnUiThread(() -> {
            showToast("Found your location! Loading nearby trails...");
            // Store location in LocationManager
            loadTrailsWithLocation(location.getLatitude(), location.getLongitude());
        });
    }

    @Override
    public void onLocationError(String error) {
        runOnUiThread(() -> {
            showToast("Could not get location: " + error + ". Using default location.");
            loadTrailsWithLocation(defaultLatitude, defaultLongitude);
        });
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
                    // Increase search radius and try again
                    double largerRadius = searchRadius * 2;
                    showToast("No trails found within " + (searchRadius/1000) + "km. Searching " + (largerRadius/1000) + "km...");

                    osmTrails = osmDataFetcher.fetchTrailsNearLocation(latitude, longitude, largerRadius);
                    if (osmTrails != null && !osmTrails.isEmpty()) {
                        trails = processOSMDataToTrails(osmTrails, osmBenches, latitude, longitude);
                    }
                }

                final List<Trail> finalTrails = trails;
                new Handler(Looper.getMainLooper()).post(() -> {
                    loadingProgressBar.setVisibility(View.GONE);

                    if (finalTrails.isEmpty()) {
                        showToast("No trails found nearby.");
                        updateEmptyStateForNoTrails();
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
                    showToast("Cannot connect to trail database. Please try again.");
                    updateEmptyStateForError();
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

                // Count benches
                int benchCount = 0;
                if (osmBenches != null) {
                    benchCount = countBenchesNearTrail(osmWay, osmBenches, 100); // 100m radius
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

                // Trail geometry
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

                // Create trail
                Trail trail = new Trail(
                        String.valueOf(osmWay.getId()),
                        name,
                        Math.round(distanceKm * 10.0) / 10.0,
                        duration,
                        benchCount,  // bench count
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
    //This can def be more creative?//
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

    private void updateEmptyStateForNoTrails() {
        emptyStateText.setText("No trails found nearby");
        emptyStateSubtext.setText("Try moving to a different location");
        retryButton.setVisibility(View.VISIBLE);
        emptyStateLayout.setVisibility(View.VISIBLE);
        recyclerViewTrails.setVisibility(View.GONE);
    }

    private void updateEmptyStateForError() {
        emptyStateText.setText("Unable to load trails");
        emptyStateSubtext.setText("Please check your internet connection and try again");
        retryButton.setVisibility(View.VISIBLE);
        emptyStateLayout.setVisibility(View.VISIBLE);
        recyclerViewTrails.setVisibility(View.GONE);
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

        // Pass user location if available from LocationManager
        if (locationManager.hasLocation() && locationManager.isLocationFresh()) {
            Location location = locationManager.getCurrentLocation();
            intent.putExtra("user_lat", location.getLatitude());
            intent.putExtra("user_lon", location.getLongitude());
            intent.putExtra("location_accuracy", location.getAccuracy());
            intent.putExtra("location_time", location.getTime());
        }

        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start location updates when activity is visible
        if (locationManager.hasLocationPermissions()) {
            locationManager.setUpdateMode(LocationManager.UpdateMode.LOW_FREQUENCY);
            locationManager.startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop location updates when activity is not visible
        locationManager.setUpdateMode(LocationManager.UpdateMode.NO_UPDATES);
        locationManager.stopLocationUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Remove location listener; for performance and avoid battery drain
        locationManager.removeLocationListener(this);

        // Shutdown executor
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}