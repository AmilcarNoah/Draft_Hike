package com.amilcarf.draft_hike.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OSMWay {
    private long id;
    private List<OSMNode> nodes;
    private Map<String, String> tags;

    public OSMWay(long id) {
        this.id = id;
        this.nodes = new ArrayList<>();
        this.tags = new HashMap<>();
    }

    public long getId() {
        return id;
    }

    public List<OSMNode> getNodes() {
        return nodes;
    }

    public void addNode(OSMNode node) {
        nodes.add(node);
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