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
}