package com.alels.gateway.model;

public class NormalizedFieldInfo {

    private Long mappingId;
    private Long deviceModelId;
    private Long normalizedFieldId;

    private String dictionaryCode;
    private String sourceProtocol;
    private String sourceIoId;
    private String sourceName;
    private String sourceUnit;
    private String targetUnit;

    private Double multiplier;
    private Double offsetValue;

    private String valueType;

    private String fieldCode;
    private String fieldName;
    private String category;
    private String unit;

    public Long getMappingId() {
        return mappingId;
    }

    public void setMappingId(Long mappingId) {
        this.mappingId = mappingId;
    }

    public Long getDeviceModelId() {
        return deviceModelId;
    }

    public void setDeviceModelId(Long deviceModelId) {
        this.deviceModelId = deviceModelId;
    }

    public Long getNormalizedFieldId() {
        return normalizedFieldId;
    }

    public void setNormalizedFieldId(Long normalizedFieldId) {
        this.normalizedFieldId = normalizedFieldId;
    }

    public String getDictionaryCode() {
        return dictionaryCode;
    }

    public void setDictionaryCode(String dictionaryCode) {
        this.dictionaryCode = dictionaryCode;
    }

    public String getSourceProtocol() {
        return sourceProtocol;
    }

    public void setSourceProtocol(String sourceProtocol) {
        this.sourceProtocol = sourceProtocol;
    }

    public String getSourceIoId() {
        return sourceIoId;
    }

    public void setSourceIoId(String sourceIoId) {
        this.sourceIoId = sourceIoId;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getSourceUnit() {
        return sourceUnit;
    }

    public void setSourceUnit(String sourceUnit) {
        this.sourceUnit = sourceUnit;
    }

    public String getTargetUnit() {
        return targetUnit;
    }

    public void setTargetUnit(String targetUnit) {
        this.targetUnit = targetUnit;
    }

    public Double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(Double multiplier) {
        this.multiplier = multiplier;
    }

    public Double getOffsetValue() {
        return offsetValue;
    }

    public void setOffsetValue(Double offsetValue) {
        this.offsetValue = offsetValue;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getFieldCode() {
        return fieldCode;
    }

    public void setFieldCode(String fieldCode) {
        this.fieldCode = fieldCode;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }
}