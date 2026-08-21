package com.alels.gateway.model;

public class AlertRule {

    private Long id;
    private Long companyId;
    private Long deviceId;
    private String imei;
    private String dictionaryCode;
    private String protocol;
    private String ioId;
    private String alertType;
    private String title;
    private String ruleCode;
    private String severity;
    private String conditionOperator;
    private Double thresholdValue;
    private String matchValue;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }

    public String getDictionaryCode() { return dictionaryCode; }
    public void setDictionaryCode(String dictionaryCode) { this.dictionaryCode = dictionaryCode; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public String getIoId() { return ioId; }
    public void setIoId(String ioId) { this.ioId = ioId; }

    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getConditionOperator() { return conditionOperator; }
    public void setConditionOperator(String conditionOperator) { this.conditionOperator = conditionOperator; }

    public Double getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(Double thresholdValue) { this.thresholdValue = thresholdValue; }

    public String getMatchValue() { return matchValue; }
    public void setMatchValue(String matchValue) { this.matchValue = matchValue; }
}
