package com.alels.backend.serverops.aiops.model;

import java.util.ArrayList;
import java.util.List;

public class AiOpsOverviewResponse {

    private String systemStatus;
    private String mainIssue;
    private String impact;
    private String recommendedAction;
    private String generatedAt;
    private List<MetricCard> metrics = new ArrayList<>();
    private List<AiOpsFinding> findings = new ArrayList<>();

    public String getSystemStatus() {
        return systemStatus;
    }

    public void setSystemStatus(String systemStatus) {
        this.systemStatus = systemStatus;
    }

    public String getMainIssue() {
        return mainIssue;
    }

    public void setMainIssue(String mainIssue) {
        this.mainIssue = mainIssue;
    }

    public String getImpact() {
        return impact;
    }

    public void setImpact(String impact) {
        this.impact = impact;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public void setRecommendedAction(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public List<MetricCard> getMetrics() {
        return metrics;
    }

    public void setMetrics(List<MetricCard> metrics) {
        this.metrics = metrics;
    }

    public List<AiOpsFinding> getFindings() {
        return findings;
    }

    public void setFindings(List<AiOpsFinding> findings) {
        this.findings = findings;
    }

    public static class MetricCard {
        private String key;
        private String label;
        private String value;
        private String unit;
        private String severity;
        private String description;

        public MetricCard(String key, String label, String value, String unit, String severity, String description) {
            this.key = key;
            this.label = label;
            this.value = value;
            this.unit = unit;
            this.severity = severity;
            this.description = description;
        }

        public String getKey() { return key; }
        public String getLabel() { return label; }
        public String getValue() { return value; }
        public String getUnit() { return unit; }
        public String getSeverity() { return severity; }
        public String getDescription() { return description; }
    }

    public static class AiOpsFinding {
        private String id;
        private String severity;
        private String category;
        private String title;
        private String problem;
        private String impact;
        private String recommendedAction;
        private String status;

        public AiOpsFinding(String id, String severity, String category, String title, String problem, String impact, String recommendedAction, String status) {
            this.id = id;
            this.severity = severity;
            this.category = category;
            this.title = title;
            this.problem = problem;
            this.impact = impact;
            this.recommendedAction = recommendedAction;
            this.status = status;
        }

        public String getId() { return id; }
        public String getSeverity() { return severity; }
        public String getCategory() { return category; }
        public String getTitle() { return title; }
        public String getProblem() { return problem; }
        public String getImpact() { return impact; }
        public String getRecommendedAction() { return recommendedAction; }
        public String getStatus() { return status; }
    }
}
