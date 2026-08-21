package com.alels.gateway.dictionary;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AvlDefinition {

    private String id;

    private String name;

    private String bytes;

    private String type;

    private String unit;

    private double multiplier;

    private String category;

    private String description;

    public AvlDefinition() {
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBytes() {
        return bytes;
    }

    public String getType() {
        return type;
    }

    public String getUnit() {
        return unit;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setType(String type) { this.type = type; }
    public void setUnit(String unit) { this.unit = unit; }
    public void setMultiplier(double multiplier) { this.multiplier = multiplier; }
    public void setCategory(String category) { this.category = category; }
}
