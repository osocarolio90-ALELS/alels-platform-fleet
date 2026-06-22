package com.alels.ingestion.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryMessage {
    public String label;
    public String imei;
    public String protocol;
    public String channel;
    public String dictionaryCode;
    public String sourceProtocol;
    public String deviceTime;
    public Long packetSequence;
    public Double latitude;
    public Double longitude;
    public Integer altitude;
    public Integer angle;
    public Integer satellites;
    public Integer speed;
    public Integer priority;
    public Integer eventIoId;
    public Double hdop;
    public Map<String, Object> ioData;
}
