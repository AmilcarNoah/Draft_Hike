package com.amilcarf.draft_hike;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.amilcarf.draft_hike.adapters.TrailAdapter;
import com.amilcarf.draft_hike.models.Trail;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerViewTrails;
    private TrailAdapter trailAdapter;
    private List<Trail> trailList;
    private ProgressBar loadingProgressBar;
    private TextView emptyStateText;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize toolbar
        View toolbar = findViewById(R.id.toolbar);
        setSupportActionBar((Toolbar) toolbar);

        // Initialize views
        recyclerViewTrails = findViewById(R.id.recyclerViewTrails);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        emptyStateText = findViewById(R.id.emptyStateText);

        // Setup RecyclerView
        setupRecyclerView();

        // Load trail data
        loadTrails();

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

    private void loadTrails() {
        // Show loading indicator
        loadingProgressBar.setVisibility(View.VISIBLE);

        // Simulate loading delay
        new Handler().postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        // Sample data; we then can design to our goals
                        List<Trail> sampleTrails = new ArrayList<>();
                        sampleTrails.add(new Trail("1", "Riverside Path", 1.2, "25 min", 5,
                                "Easy", "Open", "Scenic riverside path with multiple resting benches and water fountains.", false));
                        sampleTrails.add(new Trail("2", "Old Forest Loop", 3.8, "1.5 hours", 9,
                                "Medium", "Open", "A longer trail through old forest with several benches and viewpoints.", true));
                        sampleTrails.add(new Trail("3", "Lighthouse Route", 2.1, "45 min", 4,
                                "Easy", "Open", "Coastal route leading to a historic lighthouse with benches along the way.", false));
                        sampleTrails.add(new Trail("4", "Mountain View Trail", 4.5, "2 hours", 7,
                                "Hard", "Open", "Challenging trail with breathtaking views and resting spots.", false));
                        sampleTrails.add(new Trail("5", "Park Loop", 0.8, "15 min", 3,
                                "Easy", "Open", "Short loop around the city park with accessible benches.", true));

                        // Update adapter
                        trailAdapter.updateData(sampleTrails);

                        // Hide loading indicator
                        loadingProgressBar.setVisibility(View.GONE);

                        // Show/hide empty state
                        if (sampleTrails.isEmpty()) {
                            emptyStateText.setVisibility(View.VISIBLE);
                            recyclerViewTrails.setVisibility(View.GONE);
                        } else {
                            emptyStateText.setVisibility(View.GONE);
                            recyclerViewTrails.setVisibility(View.VISIBLE);
                        }
                    }
                }, 1500 // 1.5 second delay
        );
    }

    private void setupButtons() {
        Button btnStartHike = findViewById(R.id.btnStartHike);
        View cardSearchTrails = findViewById(R.id.cardSearchTrails);
        View cardFindBenches = findViewById(R.id.cardFindBenches);

        btnStartHike.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTrailsListActivity();
            }
        });

        cardSearchTrails.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTrailsListActivity();
            }
        });

        cardFindBenches.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openMapWithBenches();
            }
        });

        // Emergency button
        View cardEmergency = findViewById(R.id.cardEmergency);
        cardEmergency.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                makeEmergencyCall();
            }
        });

        // Settings button
        View cardSettings = findViewById(R.id.cardSettings);
        cardSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettingsActivity();
            }
        });
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

    private void toggleFavorite(Trail trail, int position) {
        // Toggle favorite status
        trail.setFavorite(!trail.isFavorite());

        // Update the item in adapter
        trailAdapter.updateItem(position, trail);

        // Show toast message
        String message = trail.isFavorite() ?
                "Added to favorites" : "Removed from favorites";
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();

        // sugg: we can use sqlite here???
    }

    private void startTrailNavigation(Trail trail) {
        // Start navigation for the selected trail, how can we improve thsi??
        Intent intent = new Intent(this, NavigationActivity.class);
        intent.putExtra("trail_id", trail.getId());
        intent.putExtra("trail_name", trail.getName());
        startActivity(intent);

        android.widget.Toast.makeText(this,
                "Starting navigation for " + trail.getName(),
                android.widget.Toast.LENGTH_SHORT).show();
    }

    private void openMapWithBenches() {
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra("show_benches", true);
        startActivity(intent);
    }

    private void makeEmergencyCall() {
        // Show emergency options dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Emergency Assistance");
        builder.setMessage("Choose an emergency option:");

        builder.setPositiveButton("Call Emergency Services",
                (dialog, which) -> {
                    Intent callIntent = new Intent(Intent.ACTION_DIAL);
                    callIntent.setData(android.net.Uri.parse("tel:112"));
                    startActivity(callIntent);
                });

        builder.setNegativeButton("Send Location to Contact",
                (dialog, which) -> {
                    sendEmergencyLocation();
                });

        builder.setNeutralButton("Cancel", null);
        builder.show();
    }

    private void sendEmergencyLocation() {
        // Send location to emergency contact
        android.widget.Toast.makeText(this,
                "Location sent to emergency contact",
                android.widget.Toast.LENGTH_SHORT).show();
    }

    private void openSettingsActivity() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
}