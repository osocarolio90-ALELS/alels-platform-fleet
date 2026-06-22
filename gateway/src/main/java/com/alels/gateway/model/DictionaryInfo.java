package com.alels.gateway.model;

public class DictionaryInfo {

    private Long id;
    private Long deviceModelId;

    private String dictionaryCode;
    private String dictionaryFile;
    private String dictionaryVersion;
    private String sourceType;
    private String sourcePath;
    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDeviceModelId() {
        return deviceModelId;
    }

    public void setDeviceModelId(Long deviceModelId) {
        this.deviceModelId = deviceModelId;
    }

    public String getDictionaryCode() {
        return dictionaryCode;
    }

    public void setDictionaryCode(String dictionaryCode) {
        this.dictionaryCode = dictionaryCode;
    }

    public String getDictionaryFile() {
        return dictionaryFile;
    }

    public void setDictionaryFile(String dictionaryFile) {
        this.dictionaryFile = dictionaryFile;
    }

    public String getDictionaryVersion() {
        return dictionaryVersion;
    }

    public void setDictionaryVersion(String dictionaryVersion) {
        this.dictionaryVersion = dictionaryVersion;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}