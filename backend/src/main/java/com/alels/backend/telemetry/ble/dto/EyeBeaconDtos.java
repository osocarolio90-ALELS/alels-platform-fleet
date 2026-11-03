package com.alels.backend.telemetry.ble.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.alels.backend.telemetry.ble.service.BleRssiDistanceEstimator;
import com.alels.gateway.parser.EyeBeaconParser;

public final class EyeBeaconDtos {
    private EyeBeaconDtos() {}

    public enum Recency { RECENT, STALE }
    public enum Transport { CELLULAR, WIFI, UNKNOWN }
    public record Identity(String protocol, String kind, String value, String namespace,
            String instanceId, String uuid, Integer major, Integer minor, String mac) {}
    public record Measurement(Integer rssiDbm, Integer batteryMillivolts, Integer temperatureRaw,
            String rawBlockHex, Boolean lowBattery, Map<Integer,String> otherParameters,
            BleRssiDistanceEstimator.Estimate distance, EyeBeaconParser.SensorData sensorData,
            Double temperatureCelsius, SensorCapture sensorCapture, EyeBeaconParser.Advertisement advertisementData) {
        public Measurement(Integer rssiDbm, Integer batteryMillivolts, Integer temperatureRaw, String rawBlockHex,
                Boolean lowBattery, Map<Integer,String> otherParameters, BleRssiDistanceEstimator.Estimate distance,
                EyeBeaconParser.SensorData sensorData, Double temperatureCelsius, SensorCapture sensorCapture) {
            this(rssiDbm,batteryMillivolts,temperatureRaw,rawBlockHex,lowBattery,otherParameters,distance,sensorData,
                    temperatureCelsius,sensorCapture,null);
        }
        public Measurement(Integer rssiDbm, Integer batteryMillivolts, Integer temperatureRaw,
                String rawBlockHex, Boolean lowBattery, Map<Integer,String> otherParameters) {
            this(rssiDbm,batteryMillivolts,temperatureRaw,rawBlockHex,lowBattery,otherParameters,null,null,null,null);
        }
        public Measurement(Integer rssiDbm, Integer batteryMillivolts, Integer temperatureRaw, String rawBlockHex) {
            this(rssiDbm,batteryMillivolts,temperatureRaw,rawBlockHex,null,Map.of());
        }
    }
    public record SensorCapture(String profile, String status, String reason) {}
    public record ReceiverGps(Double latitude, Double longitude, Integer altitude, Double hdop,
            Integer satellites, boolean valid) {}
    public record Source(Long observationId, long receiverDeviceId, String receiverImei,
            String protocol, Transport transport, String transportReason, int ioId, int blockIndex,
            Instant observedAt, Instant receivedAt, long telemetryId, Instant telemetryServerTime,
            ReceiverGps receiverGps, List<Integer> ioIds) {
        public Source(Long observationId, long receiverDeviceId, String receiverImei, String protocol,
                Transport transport, String transportReason, int ioId, int blockIndex, Instant observedAt,
                Instant receivedAt, long telemetryId, Instant telemetryServerTime, ReceiverGps receiverGps) {
            this(observationId,receiverDeviceId,receiverImei,protocol,transport,transportReason,ioId,blockIndex,
                    observedAt,receivedAt,telemetryId,telemetryServerTime,receiverGps,List.of(ioId));
        }
    }
    public record SensorInfo(String name, String verifiedMac, String registrationStatus, String type) {
        public SensorInfo(String name, String verifiedMac, String registrationStatus) {
            this(name, verifiedMac, registrationStatus, null);
        }
    }
    public record Card(long beaconId, String displayName, Identity identity, Instant lastSeen,
            Recency recency, Measurement measurement, Source source, SensorInfo sensor) {
        public Card(long beaconId, String displayName, Identity identity, Instant lastSeen,
                Recency recency, Measurement measurement, Source source) {
            this(beaconId,displayName,identity,lastSeen,recency,measurement,source,null);
        }
    }
    public record Page(List<Card> beacons, boolean hasMore, Long nextAfterId,
            boolean hasUnresolvedObservations, Instant serverTime, long recentThresholdSeconds) {}

    /** Identity keys belong to received telemetry, not to the optional BLE registry. */
    public record ReceivedCard(String beaconId, String displayName, Identity identity, Instant lastSeen,
            Recency recency, Measurement measurement, Source source, SensorInfo sensor) {
        public ReceivedCard(String beaconId, String displayName, Identity identity, Instant lastSeen,
                Recency recency, Measurement measurement, Source source) {
            this(beaconId,displayName,identity,lastSeen,recency,measurement,source,null);
        }
    }
    public record ReceivedError(long telemetryId, int ioId, String code, int byteOffset, String rawHex) {}
    public record ReceivedPage(List<ReceivedCard> beacons, List<ReceivedError> errors,
            boolean hasMore, Long nextBeforeId, Instant serverTime, long recentThresholdSeconds) {
        @com.fasterxml.jackson.annotation.JsonProperty("hasUnresolvedObservations")
        public boolean hasUnresolvedObservations() {
            return beacons.stream().anyMatch(card -> card.sensor() == null || card.sensor().verifiedMac() == null);
        }
    }
    public record ReceivedPacket(long id, Instant receivedAt, Instant observedAt, String protocol,
            String channel, String ioJson, Double latitude, Double longitude, Integer altitude,
            Double hdop, Integer satellites, String trackerModel) {
        public ReceivedPacket(long id, Instant receivedAt, Instant observedAt, String protocol, String channel,
                String ioJson, Double latitude, Double longitude, Integer altitude, Double hdop, Integer satellites) {
            this(id,receivedAt,observedAt,protocol,channel,ioJson,latitude,longitude,altitude,hdop,satellites,null);
        }
    }

    public record CardRow(long beaconId, String beaconProtocol, String identityKind, String identityValue,
            String namespace, String instanceId, String uuid, Integer major, Integer minor, String mac,
            long observationId, Instant observedAt, Instant receivedAt, Integer rssiDbm,
            Integer batteryMillivolts, Integer temperatureRaw, long receiverDeviceId, String receiverImei,
            String sourceProtocol, int ioId, int blockIndex, long telemetryId, Instant telemetryServerTime,
            Double latitude, Double longitude, Integer altitude, Double hdop, Integer satellites,
            boolean gpsValid, String rawBlockHex) {}
}
