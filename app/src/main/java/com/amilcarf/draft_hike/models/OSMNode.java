package com.amilcarf.draft_hike.models;

import java.util.HashMap;
import java.util.Map;

public class OSMNode {
    private long id;
    private double latitude;
    private double longitude;
    private Map<String, String> tags;

    public OSMNode(long id, double latitude, double longitude) {
        this.id = id;
        this.latitude = latitude;
        this.longitude = longitude;
        this.tags = new HashMap<>();
    }

    public long getId() {
        return id;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public String getTag(String key) {
        return tags.get(key);
    }

    public void addTag(String key, String value) {
        tags.put(key, value);
    }
}