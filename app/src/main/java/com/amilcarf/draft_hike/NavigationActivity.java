package com.amilcarf.draft_hike;

// Imports
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amilcarf.draft_hike.location.LocationManager;
import com.amilcarf.draft_hike.models.OSMNode;
import com.amilcarf.draft_hike.models.OSMWay;
import com.amilcarf.draft_hike.models.Trail;
import com.amilcarf.draft_hike.osm.OSMDataFetcher;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.android.material.button.MaterialButton;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NavigationActivity extends AppCompatActivity implements OnMapReadyCallback,
        LocationManager.LocationListener {

    // Map components
    private MapView mapView;
    private GoogleMap googleMap;

    // Location services
    private LocationManager locationManager;
    private Location currentLocation;

    // Trail data
    private List<LatLng> trailPoints = new ArrayList<>();
    private List<OSMTrail> osmTrails = new ArrayList<>();
    private Polyline selectedTrailPolyline;
    private Marker currentTrailMarker;
    private Marker currentLocationMarker;
    private Marker trailStartMarker;
    private Marker trailEndMarker;

    // Bench data
    private List<OSMNode> osmBenches = new ArrayList<>();
    private List<Marker> benchMarkers = new ArrayList<>();
    private boolean showBenches = true;

    // Navigation state
    private boolean isNavigationActive = false;
    private int currentTrailIndex = 0;
    private float totalDistance = 0f;
    private float remainingDistance = 0f;
    private int currentTrailSelection = 0;

    // UI Components
    private MaterialButton mabStartNavigation;
    private ProgressBar progressBar;
    private Toolbar toolbar;

    // Constants
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final String MAP_VIEW_BUNDLE_KEY = "MapViewBundleKey";
    private static final String TAG = "NavigationActivity";

    // New variables for handling passed trails
    private boolean hasPreloadedTrail = false;
    private OSMTrail preloadedTrail;
    private boolean fromTrailList = false;
    private double preloadedUserLat = 0;
    private double preloadedUserLon = 0;

    // Distance calculation improvements
    private Handler navigationUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable navigationUpdateTask;
    private long lastLocationUpdateTime = 0;
    private ProjectionResult lastProjection;

    // Projection result class for distance calculation
    private class ProjectionResult {
        int segmentIndex;
        LatLng projectedPoint;
        float distanceToTrail;

        ProjectionResult(int segmentIndex, LatLng projectedPoint) {
            this.segmentIndex = segmentIndex;
            this.projectedPoint = projectedPoint;
        }
    }

    // OSM Trail class to store trail information
    public static class OSMTrail {
        public String id;
        public String name;
        public String type;
        public double distance;
        public List<LatLng> points;
        public String difficulty;

        public OSMTrail() {
            points = new ArrayList<>();
        }

        @Override
        public String toString() {
            return "OSMTrail{" +
                    "id='" + id + '\'' +
                    ", name='" + name + '\'' +
                    ", points=" + points.size() +
                    ", distance=" + distance +
                    '}';
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);

        // Initialize components
        toolbar = findViewById(R.id.toolbar);
        mabStartNavigation = findViewById(R.id.mab_start_navigation);
        progressBar = findViewById(R.id.progress_bar);

        // Setup toolbar - for Back/Return
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        // Initialize navigation update task
        navigationUpdateTask = new Runnable() {
            @Override
            public void run() {
                if (isNavigationActive && currentLocation != null) {
                    // Even if location hasn't updated, estimate movement based on last known speed
                    estimateProgressiveDistance();

                    // Force UI update
                    updateDistanceDisplay();

                    // Smooth marker animation if we have projection
                    if (currentTrailMarker != null && lastProjection != null) {
                        // Use the last projection for smooth updates
                        currentTrailMarker.setPosition(lastProjection.projectedPoint);
                    }
                }

                // Continue updating every second
                if (isNavigationActive) {
                    navigationUpdateHandler.postDelayed(this, 1000);
                }
            }
        };

        // Check for trail from TrailsListActivity
        Trail selectedTrail = getIntent().getParcelableExtra("trail");
        fromTrailList = getIntent().getBooleanExtra("from_trail_list", false);

        if (getIntent().hasExtra("user_lat") && getIntent().hasExtra("user_lon")) {
            preloadedUserLat = getIntent().getDoubleExtra("user_lat", 0);
            preloadedUserLon = getIntent().getDoubleExtra("user_lon", 0);
        }

        if (selectedTrail != null) {
            Log.d(TAG, "Received trail from TrailsListActivity: " + selectedTrail.getName());

            // Convert Trail to OSMTrail
            OSMTrail osmTrail = convertTrailToOSMTrail(selectedTrail);

            if (osmTrail != null && osmTrail.points != null && !osmTrail.points.isEmpty()) {
                hasPreloadedTrail = true;
                preloadedTrail = osmTrail;
                osmTrails.clear();
                osmTrails.add(osmTrail);

                Log.d(TAG, "Preloaded trail has " + osmTrail.points.size() + " points");

                // Set trail name
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(selectedTrail.getName());
                }
            } else {
                Log.w(TAG, "Preloaded trail has no valid points");
                hasPreloadedTrail = false;
            }
        } else {
            Log.d(TAG, "No trail received from TrailsListActivity, will fetch trails from OSM");
        }

        // Initialize map
        initializeMap(savedInstanceState);

        // Initialize LocationManager
        locationManager = LocationManager.getInstance(this);
        locationManager.addLocationListener(this);

        setupListeners();

        checkLocationPermissions();
    }

    @Override
    public void onLocationReceived(Location location) {
        lastLocationUpdateTime = System.currentTimeMillis();
        currentLocation = location;
        updateUserLocation(location);

        // Debug log for location updates
        Log.d(TAG, "Location update: " + location.getLatitude() + ", " + location.getLongitude() +
                " | Accuracy: " + location.getAccuracy() + "m | Navigation active: " + isNavigationActive);

        // Only load trails from OSM if we don't have a preloaded trail
        if (osmTrails.isEmpty() && !isNavigationActive && !hasPreloadedTrail) {
            loadTrailsFromOSM(location);
        }

        // Load benches for preloaded trail if we haven't already
        if (hasPreloadedTrail && osmBenches.isEmpty() && showBenches && !isNavigationActive) {
            fetchBenchesAsync(location);
        }

        if (isNavigationActive) {
            updateNavigation(location);
        }
    }

    @Override
    public void onLocationError(String error) {
        Log.e(TAG, "Location error: " + error);
        Toast.makeText(this, "Location error: " + error, Toast.LENGTH_SHORT).show();
    }

    private OSMTrail convertTrailToOSMTrail(Trail trail) {
        try {
            OSMTrail osmTrail = new OSMTrail();
            osmTrail.id = trail.getId();
            osmTrail.name = trail.getName();

            // Convert distance from trail (km to meters)
            osmTrail.distance = trail.getDistance() * 1000;

            // Get coordinates from trail
            List<LatLng> coordinates = trail.getCoordinates();
            if (coordinates != null && !coordinates.isEmpty()) {
                osmTrail.points = new ArrayList<>(coordinates);

                // Recalculate distance from actual coordinates for accuracy
                if (osmTrail.points.size() > 1) {
                    osmTrail.distance = calculateTotalDistance(osmTrail.points);
                }

                Log.d(TAG, "Converted trail: " + osmTrail.name +
                        " with " + osmTrail.points.size() + " points, " +
                        osmTrail.distance + " meters");
            } else {
                // If no coordinates, try to use start/end points
                osmTrail.points = new ArrayList<>();
                if (trail.getStartLat() != 0 && trail.getStartLng() != 0) {
                    osmTrail.points.add(new LatLng(trail.getStartLat(), trail.getStartLng()));
                }
                if (trail.getEndLat() != 0 && trail.getEndLng() != 0) {
                    osmTrail.points.add(new LatLng(trail.getEndLat(), trail.getEndLng()));
                }
                Log.w(TAG, "Trail has no coordinates, using start/end points only");
            }

            // Classify difficulty
            osmTrail.difficulty = trail.getDifficulty();
            if (osmTrail.difficulty == null || osmTrail.difficulty.isEmpty()) {
                osmTrail.difficulty = "Unknown";
            }

            return osmTrail;
        } catch (Exception e) {
            Log.e(TAG, "Error converting Trail to OSMTrail: " + e.getMessage(), e);
            return null;
        }
    }

    private void initializeMap(Bundle savedInstanceState) {
        mapView = findViewById(R.id.map_view);

        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAP_VIEW_BUNDLE_KEY);
        }

        mapView.onCreate(mapViewBundle);
        mapView.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;

        // Setting up own map style
        googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style));
        // Map settings
        configureMap();

        // Handle preloaded trail from TrailsListActivity
        if (hasPreloadedTrail && preloadedTrail != null) {
            Log.d(TAG, "Displaying preloaded trail on map ready");

            // Display the trail immediately
            displayTrail(0);

            // Center map on the trail
            if (preloadedTrail.points != null && !preloadedTrail.points.isEmpty()) {
                centerMapOnTrail();

                // Also center on user location if available
                if (preloadedUserLat != 0 && preloadedUserLon != 0) {
                    LatLng userLatLng = new LatLng(preloadedUserLat, preloadedUserLon);
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 14f));
                }
            }

            // Fetch benches for preloaded trail area
            fetchBenchesAlongTrail();

            // Show start navigation button enabled
            if (mabStartNavigation != null) {
                mabStartNavigation.setEnabled(true);
            }
        } else {
            // Start location updates if no preloaded trail
            startLocationUpdates();
        }
    }

    private void configureMap() {
        if (googleMap == null) {
            Log.e(TAG, "GoogleMap is null in configureMap");
            return;
        }

        // Get UiSettings from GoogleMap
        UiSettings uiSettings = googleMap.getUiSettings();

        // Configure map settings
        uiSettings.setCompassEnabled(true);
        uiSettings.setMapToolbarEnabled(true);
        uiSettings.setMyLocationButtonEnabled(true);
        uiSettings.setZoomControlsEnabled(true);

        // Enable my location layer (permissions required)
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                googleMap.setMyLocationEnabled(true);
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException enabling my location", e);
            }
        }

        // Set initial camera position if no trail
        if (!hasPreloadedTrail) {
            CameraPosition initialPosition = new CameraPosition.Builder()
                    .target(new LatLng(0, 0))
                    .zoom(2f)
                    .build();
            googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(initialPosition));
        }
    }

    private void startLocationUpdates() {
        if (locationManager.hasLocationPermissions()) {
            // Start with LOW_FREQUENCY for discovery; performance wise context
            locationManager.setUpdateMode(LocationManager.UpdateMode.LOW_FREQUENCY);
            locationManager.startLocationUpdates();

            // Get last location to center map
            if (!hasPreloadedTrail) {
                Location lastLocation = locationManager.getCurrentLocation();
                if (lastLocation != null && locationManager.isLocationFresh()) {
                    currentLocation = lastLocation;
                    centerMapOnLocation(lastLocation);
                } else {
                    // Request single location if no fresh location available
                    locationManager.getSingleLocation(new LocationManager.LocationListener() {
                        @Override
                        public void onLocationReceived(Location location) {
                            currentLocation = location;
                            centerMapOnLocation(location);
                        }

                        @Override
                        public void onLocationError(String error) {
                            Log.e(TAG, "Error getting single location: " + error);
                        }
                    });
                }
            }
        }
    }

    private void loadTrailsFromOSM(Location location) {
        showLoading(true);

        // Execute OSM query with location
        new FetchOSMDataTask().execute(location);
    }

    private class FetchOSMDataTask extends AsyncTask<Location, Void, Void> {

        private List<OSMWay> fetchedWays = new ArrayList<>();
        private List<OSMNode> fetchedBenches = new ArrayList<>();

        @Override
        protected Void doInBackground(Location... locations) {
            if (locations == null || locations.length == 0) {
                return null;
            }

            Location location = locations[0];

            try {
                OSMDataFetcher fetcher = new OSMDataFetcher(NavigationActivity.this);

                // Fetch trails from OSM
                try {
                    fetchedWays = fetcher.fetchTrailsNearLocation(
                            location.getLatitude(),
                            location.getLongitude(),
                            1000 // radius in meters
                    );
                    Log.d(TAG, "Successfully fetched " + fetchedWays.size() + " ways from OSM");
                } catch (Exception e) {
                    Log.e(TAG, "OSM fetch failed: " + e.getMessage());
                    fetchedWays = fetcher.getFallbackTrails(
                            location.getLatitude(),
                            location.getLongitude()
                    );
                    Log.d(TAG, "Using fallback trails: " + fetchedWays.size() + " ways");
                }

                // Fetch benches from OSM
                try {
                    fetchedBenches = fetcher.fetchBenchesNearLocation(
                            location.getLatitude(),
                            location.getLongitude(),
                            1000
                    );
                    Log.d(TAG, "Successfully fetched " + fetchedBenches.size() + " benches");
                } catch (Exception e) {
                    Log.e(TAG, "Bench fetch failed: " + e.getMessage());
                }

            } catch (Exception e) {
                Log.e(TAG, "Error in FetchOSMDataTask: " + e.getMessage(), e);
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);

            // Convert OSMWays to OSMTrail objects
            osmTrails = convertOSMWaysToTrails(fetchedWays);
            osmBenches = fetchedBenches;

            showLoading(false);

            if (osmTrails.isEmpty()) {
                Toast.makeText(NavigationActivity.this,
                        "No trails found. Using sample trails.",
                        Toast.LENGTH_LONG).show();

                // Create sample trails using current location
                if (currentLocation != null) {
                    OSMDataFetcher fetcher = new OSMDataFetcher(NavigationActivity.this);
                    List<OSMWay> fallbackWays = fetcher.getFallbackTrails(
                            currentLocation.getLatitude(),
                            currentLocation.getLongitude()
                    );
                    osmTrails = convertOSMWaysToTrails(fallbackWays);
                }
            } else {
                Toast.makeText(NavigationActivity.this,
                        "Found " + osmTrails.size() + " trails and " + fetchedBenches.size() + " benches",
                        Toast.LENGTH_SHORT).show();
            }

            // Display the first trail if available
            if (!osmTrails.isEmpty()) {
                displayTrail(0);
            }

            // Display benches if enabled and available
            if (showBenches && !osmBenches.isEmpty()) {
                displayBenches();
            }
        }
    }

    private List<OSMTrail> convertOSMWaysToTrails(List<OSMWay> ways) {
        List<OSMTrail> trails = new ArrayList<>();

        if (ways == null) {
            Log.d(TAG, "Ways list is null");
            return trails;
        }

        Log.d(TAG, "Converting " + ways.size() + " ways to trails");

        for (OSMWay way : ways) {
            try {
                OSMTrail trail = new OSMTrail();
                trail.id = String.valueOf(way.getId());

                // Get name from tags
                Map<String, String> tags = way.getTags();
                if (tags != null && tags.containsKey("name")) {
                    trail.name = tags.get("name");
                } else {
                    trail.name = "Trail " + trail.id;
                }

                // Set difficulty
                if (tags != null) {
                    if (tags.containsKey("sac_scale")) {
                        trail.difficulty = tags.get("sac_scale");
                    } else if (tags.containsKey("surface")) {
                        trail.difficulty = mapSurfaceToDifficulty(tags.get("surface"));
                    } else {
                        trail.difficulty = "Unknown";
                    }
                } else {
                    trail.difficulty = "Unknown";
                }

                // Convert nodes to LatLng
                List<OSMNode> nodes = way.getNodes();
                if (nodes != null) {
                    Log.d(TAG, "Way has " + nodes.size() + " nodes");
                    for (OSMNode node : nodes) {
                        try {
                            double lat = getLatFromOSMNode(node);
                            double lon = getLonFromOSMNode(node);

                            if (lat != 0 && lon != 0) {
                                trail.points.add(new LatLng(lat, lon));
                            } else {
                                Log.w(TAG, "Invalid lat/lon for node: lat=" + lat + ", lon=" + lon);
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "Error getting lat/lon from OSMNode: " + e.getMessage());
                        }
                    }
                } else {
                    Log.w(TAG, "Way has null nodes list");
                }

                // Calculate distance
                if (trail.points.size() > 1) {
                    trail.distance = calculateTotalDistance(trail.points);
                    Log.d(TAG, "Trail " + trail.name + " has " + trail.points.size() +
                            " points, distance: " + trail.distance + "m");
                } else {
                    trail.distance = 0;
                    Log.w(TAG, "Trail " + trail.name + " has too few points: " + trail.points.size());
                }

                // Only add trails with reasonable distance
                if (trail.distance > 50 && trail.points.size() >= 2) {
                    trails.add(trail);
                    Log.d(TAG, "Added trail: " + trail.name + " with " + trail.points.size() + " points");
                } else {
                    Log.w(TAG, "Skipping trail " + trail.name + " - distance: " +
                            trail.distance + "m, points: " + trail.points.size());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error converting OSMWay to trail: " + e.getMessage());
            }
        }

        Log.d(TAG, "Total trails converted: " + trails.size());
        return trails;
    }

    // Helper methods to get lat/lon from OSMNode
    private double getLatFromOSMNode(OSMNode node) {
        try {
            // Try different getter methods
            Class<?> nodeClass = node.getClass();

            // Try getLatitude()
            try {
                Method method = nodeClass.getMethod("getLatitude");
                return (double) method.invoke(node);
            } catch (Exception e) {
                // Try getLat()
                try {
                    Method method = nodeClass.getMethod("getLat");
                    return (double) method.invoke(node);
                } catch (Exception e2) {
                    // Try direct field access
                    try {
                        Field field = nodeClass.getDeclaredField("lat");
                        field.setAccessible(true);
                        return (double) field.get(node);
                    } catch (Exception e3) {
                        try {
                            Field field = nodeClass.getDeclaredField("latitude");
                            field.setAccessible(true);
                            return (double) field.get(node);
                        } catch (Exception e4) {
                            Log.e(TAG, "Could not get lat from OSMNode");
                            return 0;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in getLatFromOSMNode: " + e.getMessage());
            return 0;
        }
    }

    private double getLonFromOSMNode(OSMNode node) {
        try {
            // Try different getter methods
            Class<?> nodeClass = node.getClass();

            // Try getLongitude()
            try {
                Method method = nodeClass.getMethod("getLongitude");
                return (double) method.invoke(node);
            } catch (Exception e) {
                // Try getLon() or getLng()
                try {
                    Method method = nodeClass.getMethod("getLon");
                    return (double) method.invoke(node);
                } catch (Exception e2) {
                    try {
                        Method method = nodeClass.getMethod("getLng");
                        return (double) method.invoke(node);
                    } catch (Exception e3) {
                        // Try direct field access
                        try {
                            Field field = nodeClass.getDeclaredField("lon");
                            field.setAccessible(true);
                            return (double) field.get(node);
                        } catch (Exception e4) {
                            try {
                                Field field = nodeClass.getDeclaredField("lng");
                                field.setAccessible(true);
                                return (double) field.get(node);
                            } catch (Exception e5) {
                                try {
                                    Field field = nodeClass.getDeclaredField("longitude");
                                    field.setAccessible(true);
                                    return (double) field.get(node);
                                } catch (Exception e6) {
                                    Log.e(TAG, "Could not get lon from OSMNode");
                                    return 0;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in getLonFromOSMNode: " + e.getMessage());
            return 0;
        }
    }

    private String mapSurfaceToDifficulty(String surface) {
        if (surface == null) return "Unknown";

        switch (surface.toLowerCase()) {
            case "paved":
            case "asphalt":
            case "concrete":
                return "Easy";
            case "gravel":
            case "compacted":
                return "Moderate";
            case "dirt":
            case "earth":
            case "ground":
                return "Moderate";
            case "grass":
            case "sand":
                return "Difficult";
            case "rock":
            case "rocky":
                return "Expert";
            default:
                return "Unknown";
        }
    }

    @SuppressLint("MissingPermission")
    private void updateUserLocation(Location location) {
        if (googleMap == null) return;

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        // Update/create user location marker
        if (currentLocationMarker == null) {
            currentLocationMarker = googleMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title("Your Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                    .flat(true));
        } else {
            currentLocationMarker.setPosition(latLng);
        }

        // Update camera if navigation is active
        if (isNavigationActive) {
            updateCameraForNavigation(latLng);
        }
    }

    private void displayTrail(int index) {
        if (index < 0 || index >= osmTrails.size()) {
            Log.e(TAG, "Invalid trail index: " + index);
            return;
        }

        OSMTrail trail = osmTrails.get(index);
        currentTrailSelection = index;
        trailPoints = trail.points;

        Log.d(TAG, "Displaying trail " + trail.name + " with " + trailPoints.size() + " points");

        if (trailPoints.isEmpty()) {
            Toast.makeText(this, "Trail has no points to display", Toast.LENGTH_SHORT).show();
            return;
        }

        // Clear previous trail display
        clearPreviousTrail();

        // Update UI with trail info
        updateTrailInfo(trail);

        // Draw trail polyline
        drawTrailPolyline(trail);

        // Add trail markers
        addTrailMarkers(trail);

        // Center map on trail if not already done
        if (!hasPreloadedTrail) {
            centerMapOnTrail();
        }

        // Calculate total distance
        totalDistance = (float) trail.distance;
        remainingDistance = totalDistance;
        updateDistanceDisplay();

        // Enable start navigation button
        if (mabStartNavigation != null) {
            mabStartNavigation.setEnabled(true);
        }
    }

    private void clearPreviousTrail() {
        // Remove previous polyline
        if (selectedTrailPolyline != null) {
            selectedTrailPolyline.remove();
            selectedTrailPolyline = null;
        }

        // Remove previous markers
        if (trailStartMarker != null) {
            trailStartMarker.remove();
            trailStartMarker = null;
        }

        if (trailEndMarker != null) {
            trailEndMarker.remove();
            trailEndMarker = null;
        }

        if (currentTrailMarker != null) {
            currentTrailMarker.remove();
            currentTrailMarker = null;
        }

        // Clear bench markers
        clearBenchMarkers();
    }

    private void drawTrailPolyline(OSMTrail trail) {
        try {
            Log.d(TAG, "Drawing polyline with " + trailPoints.size() + " points");

            // Create polyline display options
            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(trailPoints)
                    .color(getTrailColor(trail.difficulty))
                    .width(15f)  // Increased width for better visibility
                    .jointType(JointType.ROUND)
                    .startCap(new RoundCap())
                    .endCap(new RoundCap());

            // Add polyline to map
            selectedTrailPolyline = googleMap.addPolyline(polylineOptions);

            if (selectedTrailPolyline != null) {
                Log.d(TAG, "Polyline created successfully with ID: " + selectedTrailPolyline.getId());
            } else {
                Log.e(TAG, "Failed to create polyline");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error drawing polyline: " + e.getMessage());
            Toast.makeText(this, "Error drawing trail: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void addTrailMarkers(OSMTrail trail) {
        try {
            // Add start marker
            if (!trailPoints.isEmpty()) {
                LatLng startPoint = trailPoints.get(0);
                trailStartMarker = googleMap.addMarker(new MarkerOptions()
                        .position(startPoint)
                        .title("Start: " + trail.name)
                        .snippet("Distance: " + String.format("%.1f", trail.distance) + "m")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

                // Add end marker
                LatLng endPoint = trailPoints.get(trailPoints.size() - 1);
                trailEndMarker = googleMap.addMarker(new MarkerOptions()
                        .position(endPoint)
                        .title("End: " + trail.name)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

                Log.d(TAG, "Added markers: Start at " + startPoint + ", End at " + endPoint);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adding trail markers: " + e.getMessage());
        }
    }

    private void displayBenches() {
        // Clear existing bench markers
        clearBenchMarkers();

        if (googleMap == null || osmBenches == null || osmBenches.isEmpty()) {
            return;
        }

        for (OSMNode bench : osmBenches) {
            try {
                // Get latitude and longitude from OSMNode
                double lat = getLatFromOSMNode(bench);
                double lon = getLonFromOSMNode(bench);

                if (lat == 0 && lon == 0) {
                    continue; // Skip invalid coordinates
                }

                LatLng benchLocation = new LatLng(lat, lon);

                // Get bench name from tags if available
                String benchName = "Bench";
                try {
                    // Try to get tags from OSMNode
                    Method getTagsMethod = bench.getClass().getMethod("getTags");
                    @SuppressWarnings("unchecked")
                    Map<String, String> tags = (Map<String, String>) getTagsMethod.invoke(bench);

                    if (tags != null && tags.containsKey("name")) {
                        benchName = tags.get("name");
                    }
                } catch (Exception e) {
                    // Tags not available, use default name
                    Log.d(TAG, "No tags available for bench");
                }

                // Create marker for bench
                MarkerOptions markerOptions = new MarkerOptions()
                        .position(benchLocation)
                        .title(benchName)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_bench_vector))
                        .anchor(0.5f, 0.5f);

                Marker benchMarker = googleMap.addMarker(markerOptions);
                benchMarkers.add(benchMarker);

            } catch (Exception e) {
                Log.e(TAG, "Error adding bench marker: " + e.getMessage());
            }
        }

        Log.d(TAG, "Displayed " + benchMarkers.size() + " benches");
    }

    private void clearBenchMarkers() {
        for (Marker marker : benchMarkers) {
            if (marker != null) {
                marker.remove();
            }
        }
        benchMarkers.clear();
    }

    private int getTrailColor(String difficulty) {
        if (difficulty == null || difficulty.equals("Unknown")) {
            return Color.parseColor("#FF4CAF50"); // Green with alpha
        }

        switch (difficulty.toLowerCase()) {
            case "easy":
                return Color.parseColor("#FF4CAF50"); // Green
            case "moderate":
                return Color.parseColor("#FFFF9800"); // Orange
            case "difficult":
                return Color.parseColor("#FFF44336"); // Red
            case "expert":
                return Color.parseColor("#FF9C27B0"); // Purple
            default:
                return Color.parseColor("#FF2196F3"); // Blue
        }
    }

    private void updateTrailInfo(OSMTrail trail) {
        String info = trail.name + "\n" +
                "Distance: " + String.format("%.1f", trail.distance) + "m\n" +
                "Difficulty: " + trail.difficulty;

        // Use name of the trail
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(trail.name);
            getSupportActionBar().setSubtitle("Distance: " + String.format("%.1f", trail.distance) + "m | Difficulty: " + trail.difficulty);
        }

        Log.d(TAG, "Trail info: " + info);
    }

    private void centerMapOnLocation(Location location) {
        if (googleMap == null) return;

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f));
    }

    private void centerMapOnTrail() {
        if (googleMap == null) return;

        if (trailPoints != null && !trailPoints.isEmpty()) {
            try {
                LatLngBounds.Builder bounds = new LatLngBounds.Builder();
                for (LatLng point : trailPoints) {
                    bounds.include(point);
                }

                // Include user location if available
                if (currentLocation != null) {
                    bounds.include(new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude()));
                }

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        googleMap.animateCamera(
                                CameraUpdateFactory.newLatLngBounds(bounds.build(), 100)
                        );
                        Log.d(TAG, "Centered map on trail with " + trailPoints.size() + " points");
                    } catch (Exception e) {
                        Log.e(TAG, "Error centering map on bounds: " + e.getMessage());
                        // Fallback: zoom to first point
                        if (!trailPoints.isEmpty()) {
                            googleMap.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(trailPoints.get(0), 14f)
                            );
                        }
                    }
                }, 500);
            } catch (Exception e) {
                Log.e(TAG, "Error building bounds: " + e.getMessage());
            }
        }
    }

    private void setupListeners() {
        // Navigation button listener
        if (mabStartNavigation != null) {
            mabStartNavigation.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (isNavigationActive) {
                        stopNavigation();
                    } else {
                        startNavigation();
                    }
                }
            });
        }

        // Map click listener to show trail details
        if (googleMap != null) {
            googleMap.setOnPolylineClickListener(new GoogleMap.OnPolylineClickListener() {
                @Override
                public void onPolylineClick(Polyline polyline) {
                    if (selectedTrailPolyline != null && polyline.getId().equals(selectedTrailPolyline.getId())) {
                        // Show trail details
                        OSMTrail trail = osmTrails.get(currentTrailSelection);
                        String message = trail.name + "\n" +
                                "Length: " + String.format("%.1f", trail.distance) + "m\n" +
                                "Points: " + trail.points.size() + "\n" +
                                "Difficulty: " + trail.difficulty;

                        Toast.makeText(NavigationActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                }
            });

            // Add marker click listener for benches
            googleMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
                @Override
                public boolean onMarkerClick(Marker marker) {
                    if (benchMarkers.contains(marker)) {
                        Toast.makeText(NavigationActivity.this,
                                marker.getTitle() + "\nTap map to navigate here",
                                Toast.LENGTH_SHORT).show();
                        return true; // Consume the event
                    }
                    return false;
                }
            });
        }
    }

    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    // For Nav updates, tracking
    private void updateNavigation(Location currentLocation) {
        if (currentTrailIndex >= trailPoints.size() - 1) {
            // Trail completed
            Toast.makeText(this, "Trail completed! Well done!", Toast.LENGTH_LONG).show();
            stopNavigation();
            return;
        }

        // Find the closest point on the trail (projected onto segments)
        ProjectionResult projection = findClosestPointOnTrail(currentLocation);

        if (projection == null) {
            Log.w(TAG, "No projection found for current location");
            return;
        }

        // Store for continuous updates
        lastProjection = projection;

        // Update progress along the trail
        updateTrailProgress(projection);

        // Calculate remaining distance accurately
        remainingDistance = calculateAccurateRemainingDistance(
                projection.segmentIndex,
                projection.projectedPoint
        );

        // Update the display
        updateDistanceDisplay();

        // Update marker
        updateCurrentTrailMarker(projection.projectedPoint);

        // Provide hints every 10 seconds
        if (System.currentTimeMillis() % 10000 < 500) {
            provideNavigationHint(projection.segmentIndex);
        }

        // Debug log
        debugDistanceCalculation(currentLocation, projection);
    }

    /**
     * Closest point on any trail segment (not just vertices); at first vertices didnt worked as intended...
     */
    private ProjectionResult findClosestPointOnTrail(Location location) {
        if (trailPoints == null || trailPoints.size() < 2) return null;

        LatLng userPoint = new LatLng(location.getLatitude(), location.getLongitude());
        float minDistance = Float.MAX_VALUE;
        int closestSegmentIndex = 0;
        LatLng closestPoint = trailPoints.get(0);

        // Check each segment of the trail
        for (int i = 0; i < trailPoints.size() - 1; i++) {
            LatLng segStart = trailPoints.get(i);
            LatLng segEnd = trailPoints.get(i + 1);

            LatLng projection = projectPointOntoSegment(userPoint, segStart, segEnd);
            float distance = distanceBetween(userPoint, projection);

            if (distance < minDistance) {
                minDistance = distance;
                closestSegmentIndex = i;
                closestPoint = projection;
            }
        }

        ProjectionResult result = new ProjectionResult(closestSegmentIndex, closestPoint);
        result.distanceToTrail = minDistance;
        return result;
    }

    /**
     * Projects a point onto a line segment
     */
    private LatLng projectPointOntoSegment(LatLng point, LatLng segStart, LatLng segEnd) {
        // Vector A->P
        double ax = point.longitude - segStart.longitude;
        double ay = point.latitude - segStart.latitude;

        // Vector A->B
        double bx = segEnd.longitude - segStart.longitude;
        double by = segEnd.latitude - segStart.latitude;

        // Dot product
        double dot = ax * bx + ay * by;
        double lenSq = bx * bx + by * by;

        // Parameter t (projection factor)
        double t = Math.max(0, Math.min(1, dot / lenSq));

        // Calculate projected point
        double projLon = segStart.longitude + t * bx;
        double projLat = segStart.latitude + t * by;

        return new LatLng(projLat, projLon);
    }

    /**
     * Calculates distance between two LatLng points
     */
    private float distanceBetween(LatLng point1, LatLng point2) {
        float[] results = new float[1];
        Location.distanceBetween(
                point1.latitude, point1.longitude,
                point2.latitude, point2.longitude,
                results
        );
        return results[0];
    }

    private void updateTrailProgress(ProjectionResult projection) {
        // index forward if current segment passed
        if (projection.segmentIndex > currentTrailIndex) {
            currentTrailIndex = projection.segmentIndex;
            Log.d(TAG, "Progress: Moved to segment " + currentTrailIndex);
        }
        // move forward if near the end of current segment
        else if (projection.segmentIndex == currentTrailIndex) {
            LatLng currentSegEnd = trailPoints.get(currentTrailIndex + 1);
            float distanceToEnd = distanceBetween(projection.projectedPoint, currentSegEnd);

            // if within 10 meters of the segment end, move to next segment
            if (distanceToEnd < 10 && currentTrailIndex < trailPoints.size() - 2) {
                currentTrailIndex++;
                Log.d(TAG, "Progress: Advanced to next segment " + currentTrailIndex);
            }
        }
    }
//2 phases: 1 distance transversed; 2 distance remaining ...
    private float calculateAccurateRemainingDistance(int segmentIndex, LatLng currentPosition) {
        if (segmentIndex >= trailPoints.size() - 1) return 0;

        float remaining = 0f;

        // 1. Distance from current position to end of current segment
        LatLng segmentEnd = trailPoints.get(segmentIndex + 1);
        remaining += distanceBetween(currentPosition, segmentEnd);

        // 2. Distance from next point to end of trail
        for (int i = segmentIndex + 1; i < trailPoints.size() - 1; i++) {
            remaining += distanceBetween(trailPoints.get(i), trailPoints.get(i + 1));
        }

        return remaining;
    }

    private void updateCurrentTrailMarker(LatLng position) {
        if (currentTrailMarker != null) {
            currentTrailMarker.remove();
        }

        currentTrailMarker = googleMap.addMarker(new MarkerOptions()
                .position(position)
                .title("Your position on trail")
                .snippet(String.format(Locale.getDefault(),
                        "%.0f meters remaining", remainingDistance))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                .anchor(0.5f, 0.5f));
    }

    private void updateCameraForNavigation(LatLng latLng) {
        if (googleMap == null) return;

        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(latLng)
                .zoom(17f)
                .bearing(45f)
                .tilt(60f)
                .build();

        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }

    private float calculateTotalDistance(List<LatLng> points) {
        float total = 0f;

        for (int i = 0; i < points.size() - 1; i++) {
            float[] results = new float[1];
            Location.distanceBetween(
                    points.get(i).latitude,
                    points.get(i).longitude,
                    points.get(i + 1).latitude,
                    points.get(i + 1).longitude,
                    results
            );
            total += results[0];
        }

        return total;
    }

    private double[] getTrailBoundingBox(List<LatLng> trailPoints, float bufferMeters) {
        if (trailPoints == null || trailPoints.isEmpty()) {
            return null;
        }

        // Initialize with first point
        double minLat = trailPoints.get(0).latitude;
        double maxLat = trailPoints.get(0).latitude;
        double minLon = trailPoints.get(0).longitude;
        double maxLon = trailPoints.get(0).longitude;

        // Find min/max coordinates
        for (LatLng point : trailPoints) {
            minLat = Math.min(minLat, point.latitude);
            maxLat = Math.max(maxLat, point.latitude);
            minLon = Math.min(minLon, point.longitude);
            maxLon = Math.max(maxLon, point.longitude);
        }

        // Convert buffer meters to degrees (approximate)
        // 1 degree ≈ 111,000 meters
        double latBuffer = bufferMeters / 111000.0;
        double lonBuffer = bufferMeters / (111000.0 * Math.cos(Math.toRadians(minLat)));

        // Expand bounds by buffer (100m + extra margin)
        return new double[] {
                minLat - latBuffer,  // south
                minLon - lonBuffer,  // west
                maxLat + latBuffer,  // north
                maxLon + lonBuffer   // east
        };
    }

    private List<OSMNode> filterBenchesNearTrail(List<OSMNode> allBenches, List<LatLng> trailPoints, float maxDistanceMeters) {
        List<OSMNode> nearTrailBenches = new ArrayList<>();

        if (trailPoints == null || trailPoints.isEmpty() || allBenches == null) {
            return nearTrailBenches;
        }

        // Create a map of valid bench locations to avoid repeated getLat/getLon calls
        Map<OSMNode, LatLng> validBenchLocations = new HashMap<>();
        for (OSMNode bench : allBenches) {
            try {
                double benchLat = getLatFromOSMNode(bench);
                double benchLon = getLonFromOSMNode(bench);

                if (benchLat != 0 || benchLon != 0) {
                    validBenchLocations.put(bench, new LatLng(benchLat, benchLon));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting bench location: " + e.getMessage());
            }
        }

        // Early exit if no valid benches
        if (validBenchLocations.isEmpty()) {
            return nearTrailBenches;
        }

        // Process each bench
        for (Map.Entry<OSMNode, LatLng> entry : validBenchLocations.entrySet()) {
            OSMNode bench = entry.getKey();
            LatLng benchLocation = entry.getValue();

            double benchLat = benchLocation.latitude;
            double benchLon = benchLocation.longitude;

            float minDistance = Float.MAX_VALUE;

            // Check distance to each trail point
            float[] results = new float[1];
            for (LatLng trailPoint : trailPoints) {
                // Quick bounding box check first
                double latDiff = Math.abs(benchLat - trailPoint.latitude);
                double lonDiff = Math.abs(benchLon - trailPoint.longitude);

                if (latDiff * 111000 > maxDistanceMeters || lonDiff * 111000 * Math.cos(Math.toRadians(benchLat)) > maxDistanceMeters) {
                    continue;
                }

                Location.distanceBetween(benchLat, benchLon, trailPoint.latitude, trailPoint.longitude,
                        results
                );

                if (results[0] < minDistance) {
                    minDistance = results[0];

                    // Early exit if within max distance
                    if (minDistance <= maxDistanceMeters) {
                        break;
                    }
                }
            }

            if (minDistance <= maxDistanceMeters) {
                nearTrailBenches.add(bench);
                Log.d(TAG, "Bench " + bench.getId() + " is " +
                        String.format("%.1f", minDistance) + "m from trail");
            }
        }

        return nearTrailBenches;
    }

    private void updateDistanceDisplay() {
        float distanceKm = (remainingDistance / 1000);
        float totalKm = (totalDistance / 1000);

        Log.d(TAG, "Distance Display - Remaining: " + remainingDistance +
                "m (" + distanceKm + "km), Total: " + totalKm + "km");

        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(
                    String.format(Locale.getDefault(),
                            "Remaining: %.1f km / Total: %.1f km", distanceKm, totalKm));
        }
    }

    private void provideNavigationHint(int currentSegment) {
        if (currentSegment < 0 || currentSegment >= trailPoints.size() - 1) return;

        LatLng currentPoint = trailPoints.get(currentSegment);
        LatLng nextPoint = trailPoints.get(currentSegment + 1);

        // Calculate bearing to next point
        float bearing = calculateBearing(currentPoint, nextPoint);
        String direction = getDirectionFromBearing(bearing);

        // Calculate distance to next point
        float distanceToNext = distanceBetween(currentPoint, nextPoint);

        String hint = String.format(Locale.getDefault(),
                "Continue %s for %.0f meters", direction, distanceToNext);

        // Show as toast or update UI
        Toast.makeText(this, hint, Toast.LENGTH_SHORT).show();
    }

    private void estimateProgressiveDistance() {
        if (currentLocation == null || lastProjection == null) return;

        // If we have speed data, estimate distance traveled
        if (currentLocation.hasSpeed() && currentLocation.getSpeed() > 0) {
            // Speed in meters per second
            float speedMps = currentLocation.getSpeed();

            // Time since last actual location update (not this timer update)
            long timeSinceLastLocation = System.currentTimeMillis() - lastLocationUpdateTime;
            float estimatedDistanceMoved = speedMps * (timeSinceLastLocation / 1000f);

            // Subtract from remaining distance
            if (remainingDistance > estimatedDistanceMoved) {
                remainingDistance -= estimatedDistanceMoved;
                Log.d(TAG, "Estimated movement: " + estimatedDistanceMoved + "m at " + speedMps + "m/s");
            }
        }
    }

    private float calculateBearing(LatLng from, LatLng to) {
        double lat1 = Math.toRadians(from.latitude);
        double lon1 = Math.toRadians(from.longitude);
        double lat2 = Math.toRadians(to.latitude);
        double lon2 = Math.toRadians(to.longitude);

        double dLon = lon2 - lon1;

        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);

        double bearing = Math.atan2(y, x);
        return (float) Math.toDegrees(bearing);
    }

    private String getDirectionFromBearing(float bearing) {
        double normalizedBearing = (bearing + 360) % 360;

        if (normalizedBearing >= 22.5 && normalizedBearing < 67.5) {
            return "northeast";
        } else if (normalizedBearing >= 67.5 && normalizedBearing < 112.5) {
            return "east";
        } else if (normalizedBearing >= 112.5 && normalizedBearing < 157.5) {
            return "southeast";
        } else if (normalizedBearing >= 157.5 && normalizedBearing < 202.5) {
            return "south";
        } else if (normalizedBearing >= 202.5 && normalizedBearing < 247.5) {
            return "southwest";
        } else if (normalizedBearing >= 247.5 && normalizedBearing < 292.5) {
            return "west";
        } else if (normalizedBearing >= 292.5 && normalizedBearing < 337.5) {
            return "northwest";
        } else {
            return "north";
        }
    }

    private void debugDistanceCalculation(Location location, ProjectionResult projection) {
        if (location == null || projection == null) return;

        float accurateDist = calculateAccurateRemainingDistance(
                projection.segmentIndex, projection.projectedPoint);

        Log.d(TAG, String.format(Locale.getDefault(),
                "User: %.6f, %.6f | Projection: %.6f, %.6f | Segment: %d | Remaining: %.0fm",
                location.getLatitude(), location.getLongitude(),
                projection.projectedPoint.latitude, projection.projectedPoint.longitude,
                projection.segmentIndex, accurateDist));
    }

    private void startNavigation() {
        if (trailPoints == null || trailPoints.isEmpty()) {
            Toast.makeText(this, "No trail loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        isNavigationActive = true;
        currentTrailIndex = 0;
        remainingDistance = totalDistance;

        // Change update mode to HIGH_FREQUENCY for navigation
        if (locationManager != null) {
            locationManager.setUpdateMode(LocationManager.UpdateMode.HIGH_FREQUENCY);
        }

        // Change icon to stop navigation
        if (mabStartNavigation != null) {
            mabStartNavigation.setIconResource(android.R.drawable.ic_media_pause);
        }

        // Start continuous distance updates
        navigationUpdateHandler.postDelayed(navigationUpdateTask, 1000);

        // Initialize with current position
        if (currentLocation != null) {
            lastProjection = findClosestPointOnTrail(currentLocation);
            if (lastProjection != null) {
                remainingDistance = calculateAccurateRemainingDistance(
                        lastProjection.segmentIndex,
                        lastProjection.projectedPoint
                );
            }
        }

        String trailName = osmTrails.get(currentTrailSelection).name;
        Toast.makeText(this, "Navigation started on " + trailName, Toast.LENGTH_SHORT).show();

        updateDistanceDisplay();
    }

    private void stopNavigation() {
        isNavigationActive = false;

        // Change update mode back to LOW_FREQUENCY
        if (locationManager != null) {
            locationManager.setUpdateMode(LocationManager.UpdateMode.LOW_FREQUENCY);
        }

        // Change icon back to start navigation
        if (mabStartNavigation != null) {
            mabStartNavigation.setIconResource(android.R.drawable.ic_media_play);
        }

        // Stop continuous distance updates
        navigationUpdateHandler.removeCallbacks(navigationUpdateTask);

        if (currentTrailMarker != null) {
            currentTrailMarker.remove();
            currentTrailMarker = null;
        }

        Toast.makeText(this, "Navigation stopped", Toast.LENGTH_SHORT).show();
    }

    // Helper method to fetch benches asynchronously
    private void fetchBenchesAsync(Location location) {
        new AsyncTask<Location, Void, List<OSMNode>>() {
            @Override
            protected List<OSMNode> doInBackground(Location... locations) {
                try {
                    OSMDataFetcher fetcher = new OSMDataFetcher(NavigationActivity.this);
                    return fetcher.fetchBenchesNearLocation(
                            locations[0].getLatitude(),
                            locations[0].getLongitude(),
                            1000
                    );
                } catch (Exception e) {
                    Log.e(TAG, "Error fetching benches: " + e.getMessage());
                    return new ArrayList<>();
                }
            }

            @Override
            protected void onPostExecute(List<OSMNode> benches) {
                osmBenches = benches;
                if (!benches.isEmpty()) {
                    displayBenches();
                }
            }
        }.execute(location);
    }

    private void fetchBenchesAlongTrail() {
        if (preloadedTrail == null || preloadedTrail.points == null || preloadedTrail.points.isEmpty()) {
            Log.w(TAG, "No trail points for bench fetching");
            return;
        }

        // Get bounding box expanded by 200m; along with pre-filter for improved performance
        double[] bbox = getTrailBoundingBox(preloadedTrail.points, 200f);
        if (bbox == null) return;

        showLoading(true);

        new AsyncTask<Void, Void, List<OSMNode>>() {
            @Override
            protected List<OSMNode> doInBackground(Void... voids) {
                try {
                    OSMDataFetcher fetcher = new OSMDataFetcher(NavigationActivity.this);

                    // Fetch benches in the expanded bounding box
                    List<OSMNode> benchesInArea = fetcher.fetchBenchesInBoundingBox(
                            bbox[0], // south
                            bbox[1], // west
                            bbox[2], // north
                            bbox[3]  // east
                    );

                    Log.d(TAG, "Found " + benchesInArea.size() + " benches in bounding box");

                    // Filter to only benches within 100m of trail
                    return filterBenchesNearTrail(benchesInArea, preloadedTrail.points, 100f);

                } catch (Exception e) {
                    Log.e(TAG, "Error fetching benches along trail: " + e.getMessage(), e);
                    return new ArrayList<>();
                }
            }

            @Override
            protected void onPostExecute(List<OSMNode> benches) {
                showLoading(false);
                osmBenches = benches;

                // Display benches on map
                displayBenches();

                // Log and show count
                int benchCount = benches.size();
                Log.d(TAG, "Found " + benchCount + " benches within 100m of trail");

                if (benchCount > 0) {
                    Toast.makeText(NavigationActivity.this,
                            "Found " + benchCount + " benches along the trail",
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(NavigationActivity.this,
                            "No benches found near the trail",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    // Permission handling
    private void checkLocationPermissions() {
        if (!locationManager.hasLocationPermissions()) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        } else {
            // Permissions already granted, start location updates if needed
            if (!hasPreloadedTrail) {
                startLocationUpdates();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
                if (googleMap != null) {
                    try {
                        googleMap.setMyLocationEnabled(true);
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                Toast.makeText(
                        this,
                        "Location permission required for trail navigation",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    // MapView lifecycle methods
    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
        if (locationManager != null) {
            locationManager.stopLocationUpdates();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        // Stop any running handlers
        navigationUpdateHandler.removeCallbacks(navigationUpdateTask);
        if (locationManager != null) {
            locationManager.removeLocationListener(this);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        Bundle mapViewBundle = outState.getBundle(MAP_VIEW_BUNDLE_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(MAP_VIEW_BUNDLE_KEY, mapViewBundle);
        }

        mapView.onSaveInstanceState(mapViewBundle);
    }
}