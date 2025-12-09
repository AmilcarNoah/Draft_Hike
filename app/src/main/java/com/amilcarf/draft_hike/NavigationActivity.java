package com.amilcarf.draft_hike;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class NavigationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);

        // Get trail name from intent
        String trailName = getIntent().getStringExtra("trail_name");

        // Show trail name
        TextView tvTrailName = findViewById(R.id.tvTrailName);
        if (tvTrailName != null && trailName != null) {
            tvTrailName.setText("Navigating: " + trailName);
        }

        Toast.makeText(this, "Navigation started for " + trailName, Toast.LENGTH_SHORT).show();
    }
}