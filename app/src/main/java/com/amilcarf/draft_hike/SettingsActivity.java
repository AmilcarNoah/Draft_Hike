package com.amilcarf.draft_hike;

import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Initialize views that exist
        Switch switchVoiceGuidance = findViewById(R.id.switchVoiceGuidance);
        RadioGroup radioGroupTextSize = findViewById(R.id.radioGroupTextSize);
        Switch switchContrastMode = findViewById(R.id.switchContrastMode);
        Button btnDownload = findViewById(R.id.btnDownload);
        Button btnBack = findViewById(R.id.btnBack);

        // Set up listeners for views that exist
        if (switchVoiceGuidance != null) {
            switchVoiceGuidance.setOnCheckedChangeListener((buttonView, isChecked) -> {
                Toast.makeText(SettingsActivity.this,
                        "Voice guidance " + (isChecked ? "enabled" : "disabled"),
                        Toast.LENGTH_SHORT).show();
            });
        }

        if (radioGroupTextSize != null) {
            radioGroupTextSize.setOnCheckedChangeListener((group, checkedId) -> {
                String size = "Medium";
                if (checkedId == R.id.radioSmall) {
                    size = "Small";
                } else if (checkedId == R.id.radioLarge) {
                    size = "Large";
                }
                Toast.makeText(SettingsActivity.this,
                        "Text size set to " + size,
                        Toast.LENGTH_SHORT).show();
            });
        }

        if (switchContrastMode != null) {
            switchContrastMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                Toast.makeText(SettingsActivity.this,
                        "High contrast mode " + (isChecked ? "enabled" : "disabled"),
                        Toast.LENGTH_SHORT).show();
            });
        }

        if (btnDownload != null) {
            btnDownload.setOnClickListener(v -> {
                Toast.makeText(SettingsActivity.this,
                        "Downloading offline data...",
                        Toast.LENGTH_SHORT).show();
            });
        }

        // Skip emergency contact button for now
        // Or add a toast where it would be
        Toast.makeText(this, "Emergency contact feature available soon", Toast.LENGTH_SHORT).show();

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                finish(); // Close the settings activity and go back
            });
        }
    }
}