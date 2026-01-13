package com.amilcarf.draft_hike;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amilcarf.draft_hike.adapters.TrailAdapter;
import com.amilcarf.draft_hike.database.TrailDatabaseHelper;
import com.amilcarf.draft_hike.models.Trail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerViewTrails;
    private TrailAdapter trailAdapter;
    private List<Trail> trailList;
    private ProgressBar loadingProgressBar;
    private TextView emptyStateText;
    private TrailDatabaseHelper databaseHelper;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize database helper
        databaseHelper = new TrailDatabaseHelper(this);

        // Initialize toolbar
        View toolbar = findViewById(R.id.toolbar);
        setSupportActionBar((Toolbar) toolbar);

        // Initialize views
        recyclerViewTrails = findViewById(R.id.recyclerViewTrails);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        emptyStateText = findViewById(R.id.emptyStateText);

        // Setup RecyclerView
        setupRecyclerView();

        // Load trail data from database
        loadTrailsFromDatabase();

        // Setup button click listeners
        setupButtons();
    }

    private void setupRecyclerView() {
        // Initialize trail list
        trailList = new ArrayList<>();

        // Setup layout manager
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerViewTrails.setLayoutManager(layoutManager);

        // Create and set adapter
        trailAdapter = new TrailAdapter(trailList,
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

    private void loadTrailsFromDatabase() {
        // Show loading indicator
        loadingProgressBar.setVisibility(View.VISIBLE);
        emptyStateText.setVisibility(View.GONE);
        recyclerViewTrails.setVisibility(View.GONE);

        // Use background thread for database operations
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                // Get trails from database
                List<Trail> dbTrails = databaseHelper.getAllTrails();

                handler.post(() -> {
                    // Update UI on main thread
                    if (dbTrails != null && !dbTrails.isEmpty()) {
                        trailAdapter.updateData(dbTrails);
                        recyclerViewTrails.setVisibility(View.VISIBLE);
                        emptyStateText.setVisibility(View.GONE);

                        // Optional: Show toast with count
                        Toast.makeText(MainActivity.this,
                                "Loaded " + dbTrails.size() + " trails",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        // No trails found
                        emptyStateText.setText("No trails found in database");
                        emptyStateText.setVisibility(View.VISIBLE);
                        recyclerViewTrails.setVisibility(View.GONE);
                    }

                    // Hide loading indicator
                    loadingProgressBar.setVisibility(View.GONE);
                });

            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> {
                    loadingProgressBar.setVisibility(View.GONE);
                    emptyStateText.setText("Error loading trails: " + e.getMessage());
                    emptyStateText.setVisibility(View.VISIBLE);
                    recyclerViewTrails.setVisibility(View.GONE);

                    Toast.makeText(MainActivity.this,
                            "Database error: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void toggleFavorite(Trail trail, int position) {
        // Toggle favorite status
        trail.setFavorite(!trail.isFavorite());

        // Update in database on background thread
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            databaseHelper.updateFavoriteStatus(trail.getId(), trail.isFavorite());

            handler.post(() -> {
                // Update UI on main thread
                trailAdapter.updateItem(position, trail);

                String message = trail.isFavorite() ?
                        "Added to favorites" : "Removed from favorites";
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Close database connection when activity is destroyed
        if (databaseHelper != null) {
            databaseHelper.close();
        }
    }

    // Rest of your methods remain the same...
    private void setupButtons() {
        Button btnStartHike = findViewById(R.id.btnStartHike);
        View cardSearchTrails = findViewById(R.id.cardSearchTrails);
        View cardFindBenches = findViewById(R.id.cardFindBenches);

        btnStartHike.setOnClickListener(v -> openTrailsListActivity());
        cardSearchTrails.setOnClickListener(v -> openTrailsListActivity());
        cardFindBenches.setOnClickListener(v -> openMapWithBenches());

        // Emergency button
        View cardEmergency = findViewById(R.id.cardEmergency);
        cardEmergency.setOnClickListener(v -> makeEmergencyCall());

        // Settings button
        View cardSettings = findViewById(R.id.cardSettings);
        cardSettings.setOnClickListener(v -> openSettingsActivity());
    }

    private void openTrailsListActivity() {
        Intent intent = new Intent(this, TrailsListActivity.class);
        startActivity(intent);
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

    private void startTrailNavigation(Trail trail) {
        Intent intent = new Intent(this, com.amilcarf.draft_hike.NavigationActivity.class);
        intent.putExtra("trail_osm_id", trail.getId());
        intent.putExtra("trail_name", trail.getName());
        startActivity(intent);

        Toast.makeText(this,
                "Starting navigation for " + trail.getName(),
                Toast.LENGTH_SHORT).show();
    }

    private void openMapWithBenches() {
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra("show_benches", true);
        startActivity(intent);
    }

    private void makeEmergencyCall() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Emergency Assistance");
        builder.setPositiveButton("Call Emergency Services",
                (dialog, which) -> {
                    Intent callIntent = new Intent(Intent.ACTION_DIAL);
                    callIntent.setData(android.net.Uri.parse("tel:112"));
                    startActivity(callIntent);
                });
        builder.setNeutralButton("Cancel", null);
        builder.show();
    }

    private void openSettingsActivity() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
}