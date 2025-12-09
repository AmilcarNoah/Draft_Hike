package com.amilcarf.draft_hike;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
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

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "MapActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private Location currentLocation;
    private boolean showBenches;

    // Overpass API URL template for benches
    private static final String OVERPASS_API_URL =
            "https://overpass-api.de/api/interpreter?data=[out:json];" +
                    "node[\"amenity\"=\"bench\"]" +
                    "(around:1000,%f,%f);" +
                    "out body;";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map); // You'll need to create this layout

        // Check if we should show benches
        Bundle extras = getIntent().getExtras();
        showBenches = extras != null && extras.getBoolean("show_benches", false);

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Check location permission
        if (checkLocationPermission()) {
            enableMyLocation();
        } else {
            requestLocationPermission();
        }

        // Set map click listener
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                // You can add functionality here if needed
            }
        });
    }

    private boolean checkLocationPermission() {
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
                Toast.makeText(this, "Location permission denied. Cannot show benches.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            getCurrentLocation();
        }
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Task<Location> task = fusedLocationClient.getLastLocation();
        task.addOnSuccessListener(this, new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    currentLocation = location;
                    LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());

                    // Move camera to current location
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f));

                    // Add marker for current location
                    mMap.addMarker(new MarkerOptions()
                            .position(currentLatLng)
                            .title("Your Location")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

                    // If we need to show benches, fetch them
                    if (showBenches) {
                        findNearbyBenches();
                    }
                } else {
                    Toast.makeText(MapActivity.this, "Unable to get current location", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void findNearbyBenches() {
        if (currentLocation == null) {
            Toast.makeText(this, "Getting location...", Toast.LENGTH_SHORT).show();
            getCurrentLocation();
            return;
        }

        // Clear any existing markers (except current location)
        mMap.clear();

        // Re-add current location marker
        LatLng currentLatLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
        mMap.addMarker(new MarkerOptions()
                .position(currentLatLng)
                .title("Your Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

        // Start async task to fetch benches
        new FetchBenchesTask().execute(
                currentLocation.getLatitude(),
                currentLocation.getLongitude()
        );
    }

    private class FetchBenchesTask extends AsyncTask<Double, Void, List<Bench>> {

        @Override
        protected List<Bench> doInBackground(Double... params) {
            double latitude = params[0];
            double longitude = params[1];

            List<Bench> benches = new ArrayList<>();

            try {
                String urlString = String.format(OVERPASS_API_URL, latitude, longitude);
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000); // 10 seconds
                connection.setReadTimeout(10000); // 10 seconds

                InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder result = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }

                // Parse JSON response
                benches = parseBenchesFromJSON(result.toString());

                reader.close();
                inputStream.close();
                connection.disconnect();

            } catch (IOException | JSONException e) {
                Log.e(TAG, "Error fetching benches: " + e.getMessage());
                e.printStackTrace();
            }

            return benches;
        }

        @Override
        protected void onPostExecute(List<Bench> benches) {
            super.onPostExecute(benches);

            if (benches.isEmpty()) {
                Toast.makeText(MapActivity.this,
                        "No benches found within 1km radius",
                        Toast.LENGTH_LONG).show();
                return;
            }

            // Markers for each bench
            for (Bench bench : benches) {
                LatLng benchLocation = new LatLng(bench.getLatitude(), bench.getLongitude());
                mMap.addMarker(new MarkerOptions()
                        .position(benchLocation)
                        .title("Bench")
                        .snippet(bench.getName())
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
            }

            Toast.makeText(MapActivity.this,
                    "Found " + benches.size() + " benches within 1km",
                    Toast.LENGTH_SHORT).show();
        }

        private List<Bench> parseBenchesFromJSON(String jsonString) throws JSONException {
            List<Bench> benches = new ArrayList<>();

            JSONObject json = new JSONObject(jsonString);
            JSONArray elements = json.getJSONArray("elements");

            for (int i = 0; i < elements.length(); i++) {
                JSONObject element = elements.getJSONObject(i);

                // Only process nodes (type "node")
                if ("node".equals(element.getString("type"))) {
                    double lat = element.getDouble("lat");
                    double lon = element.getDouble("lon");

                    String benchName = "Bench";
                    // Check if bench has additional information
                    if (element.has("tags")) {
                        JSONObject tags = element.getJSONObject("tags");
                        if (tags.has("name") && !tags.getString("name").isEmpty()) {
                            benchName = tags.getString("name");
                        } else if (tags.has("backrest") && "yes".equals(tags.getString("backrest"))) {
                            benchName = "Bench (with backrest)";
                        }
                    }

                    benches.add(new Bench(lat, lon, benchName));
                }
            }

            return benches;
        }
    }

    // Helper class for bench data
    private static class Bench {
        private double latitude;
        private double longitude;
        private String name;

        public Bench(double latitude, double longitude, String name) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.name = name;
        }

        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public String getName() { return name; }
    }
}