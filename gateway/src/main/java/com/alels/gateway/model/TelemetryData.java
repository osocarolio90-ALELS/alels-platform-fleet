package com.alels.gateway.model;

import java.util.LinkedHashMap;
import java.util.Map;

import com.alels.gateway.detector.ProtocolType;

public class TelemetryData {
    private String imei;
    private ProtocolType sourceProtocol;

    private String deviceTime;
    private long packetSequence;

    private double latitude;
    private double longitude;

    private int altitude;
    private int angle;
    private int satellites;
    private int speed;
    private int priority;
    private int eventIoId;

    private double hdop;

    private Map<String, Object> ioData = new LinkedHashMap<>();

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }

    public ProtocolType getSourceProtocol() {
        return sourceProtocol;
    }

    public void setSourceProtocol(ProtocolType sourceProtocol) {
        this.sourceProtocol = sourceProtocol;
    }

    public String getDeviceTime() {
        return deviceTime;
    }

    public void setDeviceTime(String deviceTime) {
        this.deviceTime = deviceTime;
    }

    public long getPacketSequence() {
        return packetSequence;
    }

    public void setPacketSequence(long packetSequence) {
        this.packetSequence = packetSequence;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public int getAltitude() {
        return altitude;
    }

    public void setAltitude(int altitude) {
        this.altitude = altitude;
    }

    public int getAngle() {
        return angle;
    }

    public void setAngle(int angle) {
        this.angle = angle;
    }

    public int getSatellites() {
        return satellites;
    }

    public void setSatellites(int satellites) {
        this.satellites = satellites;
    }

    public int getSpeed() {
        return speed;
    }

    public void setSpeed(int speed) {
        this.speed = speed;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getEventIoId() {
        return eventIoId;
    }

    public void setEventIoId(int eventIoId) {
        this.eventIoId = eventIoId;
    }

    public double getHdop() {
        return hdop;
    }

    public void setHdop(double hdop) {
        this.hdop = hdop;
    }

    public Map<String, Object> getIoData() {
        return ioData;
    }

    public void setIoData(Map<String, Object> ioData) {
        this.ioData = ioData;
    }

    public void print() {
        System.out.println("========== NORMALIZED TELEMETRY ==========");
        System.out.println("IMEI      : " + imei);
        System.out.println("PROTOCOL  : " + sourceProtocol);
        System.out.println("TIME      : " + deviceTime);
        System.out.println("Q         : " + packetSequence);
        System.out.println("LAT       : " + latitude);
        System.out.println("LON       : " + longitude);
        System.out.println("SPEED     : " + speed);
        System.out.println("ANGLE     : " + angle);
        System.out.println("ALT       : " + altitude);
        System.out.println("SAT       : " + satellites);
        System.out.println("HDOP      : " + hdop);
        System.out.println("PRIORITY  : " + priority);
        System.out.println("EVENT IO  : " + eventIoId);
        System.out.println("IO DATA   : " + ioData);
        System.out.println("==========================================");
    }
}