package com.amilcarf.draft_hike.models;

public class Trail {
    private String id;
    private String name;
    private double distance;
    private String duration;
    private int benchCount;
    private String difficulty;
    private String status;
    private String description;
    private boolean isFavorite;

    // Default constructor (required for Firebase/Firestore) ----Further reading rquired
    public Trail() {
    }

    // Constructor with parameters
    public Trail(String id, String name, double distance, String duration, int benchCount,
                 String difficulty, String status, String description, boolean isFavorite) {
        this.id = id;
        this.name = name;
        this.distance = distance;
        this.duration = duration;
        this.benchCount = benchCount;
        this.difficulty = difficulty;
        this.status = status;
        this.description = description;
        this.isFavorite = isFavorite;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public int getBenchCount() {
        return benchCount;
    }

    public void setBenchCount(int benchCount) {
        this.benchCount = benchCount;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }
}