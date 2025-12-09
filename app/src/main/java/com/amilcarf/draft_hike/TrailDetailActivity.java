package com.amilcarf.draft_hike;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class TrailDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trail_detail);

        // Get trail data from the intent
        String trailName = getIntent().getStringExtra("trail_name");
        double distance = getIntent().getDoubleExtra("trail_distance", 0);
        String duration = getIntent().getStringExtra("trail_duration");
        int benches = getIntent().getIntExtra("trail_benches", 0);
        String difficulty = getIntent().getStringExtra("trail_difficulty");
        String description = getIntent().getStringExtra("trail_description");

        // Initialize views
        TextView tvTrailName = findViewById(R.id.tvTrailName);
        TextView tvDistance = findViewById(R.id.tvDistance);
        TextView tvDuration = findViewById(R.id.tvDuration);
        TextView tvBenches = findViewById(R.id.tvBenches);
        TextView tvDifficulty = findViewById(R.id.tvDifficulty);
        TextView tvDescription = findViewById(R.id.tvDescription);
        Button btnStartNavigation = findViewById(R.id.btnStartNavigation);
        Button btnBack = findViewById(R.id.btnBack);

        // Configuration for trail data
        if (trailName != null) {
            tvTrailName.setText(trailName);
        }
        tvDistance.setText("Distance: " + String.format("%.1f km", distance));
        if (duration != null) {
            tvDuration.setText("Duration: " + duration);
        }
        tvBenches.setText("Benches: " + benches + " benches");
        if (difficulty != null) {
            tvDifficulty.setText("Difficulty: " + difficulty);
        }
        if (description != null) {
            tvDescription.setText("Description: " + description);
        }

        // Button listeners
        if (btnStartNavigation != null) {
            btnStartNavigation.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(TrailDetailActivity.this,
                            "Starting navigation for " + trailName,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish(); // Close activity
                }
            });
        }
    }
}