package com.alels.gateway.model;

public class DictionaryAvlInfo {

    private String avlId;
    private String name;
    private String unit;
    private String valueType;
    private Double multiplier;
    private String category;

    public String getAvlId() { return avlId; }
    public void setAvlId(String avlId) { this.avlId = avlId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getValueType() { return valueType; }
    public void setValueType(String valueType) { this.valueType = valueType; }

    public Double getMultiplier() { return multiplier; }
    public void setMultiplier(Double multiplier) { this.multiplier = multiplier; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}