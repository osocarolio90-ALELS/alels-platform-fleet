package com.alels.gateway.dictionary;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceDictionary {

    private String model;

    private String source;

    private int total_avl;

    private Map<String, AvlDefinition> avl;

    public DeviceDictionary() {
    }

    public String getModel() {
        return model;
    }

    public String getSource() {
        return source;
    }

    public int getTotal_avl() {
        return total_avl;
    }

    public Map<String, AvlDefinition> getAvl() {
        return avl;
    }

    public AvlDefinition find(String avlId) {
        if (avl == null) {
            return null;
        }

        return avl.get(avlId);
    }

    public void setModel(String model) { this.model = model; }
    public void setSource(String source) { this.source = source; }
    public void setTotal_avl(int totalAvl) { this.total_avl = totalAvl; }
    public void setAvl(Map<String, AvlDefinition> avl) { this.avl = avl; }
}
