package com.alels.backend.serverops.traffic.model;

import java.util.ArrayList;
import java.util.List;

public class TrafficMonitorOverviewResponse {
    private String status;
    private int trafficScore;
    private String generatedAt;
    private String summary;
    private String recommendation;
    private List<TrafficMetric> metrics = new ArrayList<>();
    private List<TrafficDistribution> protocolDistribution = new ArrayList<>();
    private List<TrafficDistribution> channelDistribution = new ArrayList<>();
    private List<TrafficInsight> insights = new ArrayList<>();

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getTrafficScore() { return trafficScore; }
    public void setTrafficScore(int trafficScore) { this.trafficScore = trafficScore; }
    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public List<TrafficMetric> getMetrics() { return metrics; }
    public void setMetrics(List<TrafficMetric> metrics) { this.metrics = metrics; }
    public List<TrafficDistribution> getProtocolDistribution() { return protocolDistribution; }
    public void setProtocolDistribution(List<TrafficDistribution> protocolDistribution) { this.protocolDistribution = protocolDistribution; }
    public List<TrafficDistribution> getChannelDistribution() { return channelDistribution; }
    public void setChannelDistribution(List<TrafficDistribution> channelDistribution) { this.channelDistribution = channelDistribution; }
    public List<TrafficInsight> getInsights() { return insights; }
    public void setInsights(List<TrafficInsight> insights) { this.insights = insights; }

    public static class TrafficMetric {
        private String key;
        private String label;
        private String value;
        private String unit;
        private String severity;
        private String description;

        public TrafficMetric() {}

        public TrafficMetric(String key, String label, String value, String unit, String severity, String description) {
            this.key = key;
            this.label = label;
            this.value = value;
            this.unit = unit;
            this.severity = severity;
            this.description = description;
        }

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class TrafficDistribution {
        private String name;
        private long count;
        private double percent;

        public TrafficDistribution() {}

        public TrafficDistribution(String name, long count, double percent) {
            this.name = name;
            this.count = count;
            this.percent = percent;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
        public double getPercent() { return percent; }
        public void setPercent(double percent) { this.percent = percent; }
    }

    public static class TrafficInsight {
        private String id;
        private String severity;
        private String category;
        private String title;
        private String impact;
        private String action;
        private String status;

        public TrafficInsight() {}

        public TrafficInsight(String id, String severity, String category, String title, String impact, String action, String status) {
            this.id = id;
            this.severity = severity;
            this.category = category;
            this.title = title;
            this.impact = impact;
            this.action = action;
            this.status = status;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getImpact() { return impact; }
        public void setImpact(String impact) { this.impact = impact; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
