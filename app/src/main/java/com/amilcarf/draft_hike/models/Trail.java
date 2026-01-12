package com.amilcarf.draft_hike.models;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;

public class Trail implements Parcelable {
    private String id;
    private String name;
    private double distance;
    private String duration;
    private int benchCount;
    private String difficulty;
    private String status;
    private String description;
    private boolean isFavorite;

    // New fields for trail geometry
    private List<LatLng> coordinates;
    private String polyline; // Encoded polyline string (more efficient storage)
    private double startLat;
    private double startLng;
    private double endLat;
    private double endLng;

    // Default constructor (required for Firebase/Firestore)
    public Trail() {
        coordinates = new ArrayList<>();
    }

    // Constructor with parameters (existing)
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
        this.coordinates = new ArrayList<>();
    }

    // New constructor with geometry parameters
    public Trail(String id, String name, double distance, String duration, int benchCount,
                 String difficulty, String status, String description, boolean isFavorite,
                 List<LatLng> coordinates, String polyline,
                 double startLat, double startLng, double endLat, double endLng) {
        this.id = id;
        this.name = name;
        this.distance = distance;
        this.duration = duration;
        this.benchCount = benchCount;
        this.difficulty = difficulty;
        this.status = status;
        this.description = description;
        this.isFavorite = isFavorite;
        this.coordinates = coordinates != null ? coordinates : new ArrayList<>();
        this.polyline = polyline;
        this.startLat = startLat;
        this.startLng = startLng;
        this.endLat = endLat;
        this.endLng = endLng;
    }

    // Parcelable implementation for passing between activities/fragments
    protected Trail(Parcel in) {
        id = in.readString();
        name = in.readString();
        distance = in.readDouble();
        duration = in.readString();
        benchCount = in.readInt();
        difficulty = in.readString();
        status = in.readString();
        description = in.readString();
        isFavorite = in.readByte() != 0;
        coordinates = in.createTypedArrayList(LatLng.CREATOR);
        polyline = in.readString();
        startLat = in.readDouble();
        startLng = in.readDouble();
        endLat = in.readDouble();
        endLng = in.readDouble();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeDouble(distance);
        dest.writeString(duration);
        dest.writeInt(benchCount);
        dest.writeString(difficulty);
        dest.writeString(status);
        dest.writeString(description);
        dest.writeByte((byte) (isFavorite ? 1 : 0));
        dest.writeTypedList(coordinates);
        dest.writeString(polyline);
        dest.writeDouble(startLat);
        dest.writeDouble(startLng);
        dest.writeDouble(endLat);
        dest.writeDouble(endLng);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Trail> CREATOR = new Creator<Trail>() {
        @Override
        public Trail createFromParcel(Parcel in) {
            return new Trail(in);
        }

        @Override
        public Trail[] newArray(int size) {
            return new Trail[size];
        }
    };

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

    // New geometry getters and setters
    public List<LatLng> getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(List<LatLng> coordinates) {
        this.coordinates = coordinates;
    }

    public String getPolyline() {
        return polyline;
    }

    public void setPolyline(String polyline) {
        this.polyline = polyline;
    }

    public double getStartLat() {
        return startLat;
    }

    public void setStartLat(double startLat) {
        this.startLat = startLat;
    }

    public double getStartLng() {
        return startLng;
    }

    public void setStartLng(double startLng) {
        this.startLng = startLng;
    }

    public double getEndLat() {
        return endLat;
    }

    public void setEndLat(double endLat) {
        this.endLat = endLat;
    }

    public double getEndLng() {
        return endLng;
    }

    public void setEndLng(double endLng) {
        this.endLng = endLng;
    }

    // Helper method to add a coordinate
    public void addCoordinate(LatLng coordinate) {
        if (coordinates == null) {
            coordinates = new ArrayList<>();
        }
        coordinates.add(coordinate);
    }

    // Helper method to check if trail has geometry
    public boolean hasGeometry() {
        return (coordinates != null && !coordinates.isEmpty()) || polyline != null;
    }
}