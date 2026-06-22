package com.alels.backend.serverops.overview.model;

import java.util.ArrayList;
import java.util.List;

public class OverviewMonitorOverviewResponse {
    private String status;
    private int overviewScore;
    private String generatedAt;
    private String summary;
    private String recommendation;
    private List<OverviewMetric> metrics = new ArrayList<>();
    private List<MonitorModule> modules = new ArrayList<>();
    private List<OverviewDistribution> healthDistribution = new ArrayList<>();
    private List<OverviewDistribution> priorityDistribution = new ArrayList<>();
    private List<OverviewInsight> insights = new ArrayList<>();

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getOverviewScore() { return overviewScore; }
    public void setOverviewScore(int overviewScore) { this.overviewScore = overviewScore; }
    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public List<OverviewMetric> getMetrics() { return metrics; }
    public void setMetrics(List<OverviewMetric> metrics) { this.metrics = metrics; }
    public List<MonitorModule> getModules() { return modules; }
    public void setModules(List<MonitorModule> modules) { this.modules = modules; }
    public List<OverviewDistribution> getHealthDistribution() { return healthDistribution; }
    public void setHealthDistribution(List<OverviewDistribution> healthDistribution) { this.healthDistribution = healthDistribution; }
    public List<OverviewDistribution> getPriorityDistribution() { return priorityDistribution; }
    public void setPriorityDistribution(List<OverviewDistribution> priorityDistribution) { this.priorityDistribution = priorityDistribution; }
    public List<OverviewInsight> getInsights() { return insights; }
    public void setInsights(List<OverviewInsight> insights) { this.insights = insights; }

    public static class OverviewMetric {
        private String key;
        private String label;
        private String value;
        private String unit;
        private String severity;
        private String description;

        public OverviewMetric() {}

        public OverviewMetric(String key, String label, String value, String unit, String severity, String description) {
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

    public static class MonitorModule {
        private String key;
        private String label;
        private String status;
        private int score;
        private String summary;
        private String recommendation;

        public MonitorModule() {}

        public MonitorModule(String key, String label, String status, int score, String summary, String recommendation) {
            this.key = key;
            this.label = label;
            this.status = status;
            this.score = score;
            this.summary = summary;
            this.recommendation = recommendation;
        }

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getRecommendation() { return recommendation; }
        public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    }

    public static class OverviewDistribution {
        private String name;
        private long count;
        private double percent;
        private String displayValue;

        public OverviewDistribution() {}

        public OverviewDistribution(String name, long count, double percent, String displayValue) {
            this.name = name;
            this.count = count;
            this.percent = percent;
            this.displayValue = displayValue;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
        public double getPercent() { return percent; }
        public void setPercent(double percent) { this.percent = percent; }
        public String getDisplayValue() { return displayValue; }
        public void setDisplayValue(String displayValue) { this.displayValue = displayValue; }
    }

    public static class OverviewInsight {
        private String id;
        private String severity;
        private String category;
        private String title;
        private String impact;
        private String action;
        private String status;

        public OverviewInsight() {}

        public OverviewInsight(String id, String severity, String category, String title, String impact, String action, String status) {
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
