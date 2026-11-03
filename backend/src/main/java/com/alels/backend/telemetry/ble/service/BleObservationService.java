package com.alels.backend.telemetry.ble.service;

import java.util.List;
import java.util.Map;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alels.gateway.model.TelemetryData;
import com.alels.gateway.parser.EyeBeaconParser;
import com.alels.gateway.normalizer.EyeBeaconTelemetryNormalizer;
import com.alels.backend.telemetry.ble.dto.EyeBeaconDtos.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.telemetry.ble.repository.BleObservationRepository;
import com.alels.backend.telemetry.device.repository.TelemetryDeviceRepository;
import com.alels.backend.telemetry.device.repository.TelemetryDeviceRepository.DeviceAccess;
import com.alels.backend.telemetry.ble.dto.EyeBeaconDtos;
import com.alels.backend.telemetry.ble.dto.EyeBeaconDtos.Card;
import com.alels.backend.telemetry.ble.dto.EyeBeaconDtos.CardRow;
import com.alels.backend.telemetry.ble.dto.EyeBeaconDtos.Page;

@Service
public class BleObservationService {
    private final BleObservationRepository repository;
    private final TelemetryDeviceRepository devices;
    private final long recentThresholdSeconds;
    private final Clock clock;
    private final BleRssiDistanceEstimator distanceEstimator;
    private static final java.util.Set<String> MANUAL_CAN_385_MODELS = java.util.Set.of(
            "FMB640","FMC640","FMM640","FMC650","FMM650");
    private Map<String,String> identityMappings = Map.of();
    private Map<String,String> captureProfiles = Map.of();
    private java.util.Set<String> macIdentitySources = java.util.Set.of();
    @Autowired
    void configureCaptureMetadata(@Value("${alels.ble.identity.associations:}") String identities,
            @Value("${alels.ble.capture.profiles:}") String profiles,
            @Value("${alels.ble.capture.mac-identities:}") String macSources) {
        identityMappings=configEntries(identities,true);captureProfiles=configEntries(profiles,false);
        macIdentitySources=java.util.Arrays.stream(macSources.split(",")).map(String::trim)
                .filter(v->!v.isBlank()).map(v->v.toUpperCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private Map<String,String> avlProfiles = Map.of();
    private Map<String,String> slotIdentities = Map.of();

    @Autowired
    void configureSensorAvlProfiles(@Value("${alels.ble.sensor.avl-profiles:}") String profiles,
            @Value("${alels.ble.sensor.slot-identities:}") String slots) {
        avlProfiles=configEntries(profiles,false); slotIdentities=configEntries(slots,true);
    }
    private Map<String,String> configEntries(String config,boolean macValues) {
        Map<String,String> result=new LinkedHashMap<>();
        for(String entry:config.split(",")) {
            String[] parts=entry.trim().split("=",-1);if(parts.length!=2)continue;
            String value=parts[1].trim().toUpperCase(java.util.Locale.ROOT);
            if(macValues)try{value=BleMacAddress.normalizeMac(value);}catch(IllegalArgumentException invalid){continue;}
            result.merge(parts[0].trim().toUpperCase(java.util.Locale.ROOT),value,(a,b)->a.equals(b)?a:"AMBIGUOUS");
        }
        return Map.copyOf(result);
    }
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Autowired
    public BleObservationService(BleObservationRepository repository, TelemetryDeviceRepository devices,
            @Value("${alels.ble.recent-threshold-seconds:300}") long seconds, BleRssiDistanceEstimator estimator) {
        this(repository,devices,seconds,Clock.systemUTC(),estimator);
    }
    public BleObservationService(BleObservationRepository repository, TelemetryDeviceRepository devices,long seconds) {
        this(repository,devices,seconds,Clock.systemUTC(),new BleRssiDistanceEstimator(-59,2,"GENERIC_UNCALIBRATED"));
    }
    BleObservationService(BleObservationRepository repository, TelemetryDeviceRepository devices,long seconds,
            Clock clock,BleRssiDistanceEstimator estimator) {
        if(seconds<=0)throw new IllegalArgumentException("BLE recent threshold must be positive");
        this.repository=repository;this.devices=devices;this.recentThresholdSeconds=seconds;
        this.clock=clock;this.distanceEstimator=estimator;
    }
    private String historicalCapturedId(CardRow row) {
        String value=row.identityValue();
        if("GATEWAY_CONFIGURED_ID".equals(row.identityKind()) && value!=null)return value.substring(value.lastIndexOf(':')+1);
        return value;
    }

    /** BLE projection only: source telemetry and transport transactions remain unchanged. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${alels.ble.projection.delay-ms:5000}")
    public void persistParsedTelemetry() {
        if(!repository.decodedStorageAvailable())return;
        String configuration=decoderConfigurationStamp();
        for(var pending:repository.pendingDecodedPackets(25,configuration)) {
            try {
                var access=new DeviceAccess(pending.gatewayId(),pending.imei(),pending.companyId());
                var page=parseReceivedPackets(access,List.of(pending.packet()),1);
                repository.storeDecodedPacket(pending,page,mapper,configuration);
            } catch(RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(BleObservationService.class).error(
                    "event=ble_projection_failed telemetry_id={} retry=true",pending.packet().id(),failure);
            }
        }
    }
    /** Metadata stamp refreshes decoded projections when verified capture rules change. Source identity is unchanged. */
    String decoderConfigurationStamp() {
        String metadata="ble-capture-instance-policy-20261103:"+new java.util.TreeMap<>(captureProfiles)+new java.util.TreeMap<>(identityMappings)
                +new java.util.TreeSet<>(macIdentitySources)+new java.util.TreeMap<>(avlProfiles)+new java.util.TreeMap<>(slotIdentities);
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException unavailable){throw new IllegalStateException(unavailable);}
    }
    private int limit(int requested) { return Math.max(1, Math.min(requested, 100)); }
    public List<Map<String, Object>> beacons(JwtUserContext user, long afterId, int requested) {
        return repository.beacons(user.companyId(), Math.max(0, afterId), limit(requested));
    }
    public List<Map<String, Object>> history(JwtUserContext user, long beaconId, long beforeId, int requested) {
        return repository.byBeacon(user.companyId(), beaconId, beforeId <= 0 ? Long.MAX_VALUE : beforeId,
                limit(requested));
    }
    public List<Map<String, Object>> gatewayHistory(JwtUserContext user, long gatewayId,
                                                     long beforeId, int requested) {
        var access = gatewayAccess(user, gatewayId);
        return repository.byGateway(access.companyId(), gatewayId,
                beforeId <= 0 ? Long.MAX_VALUE : beforeId, limit(requested));
    }

    private DeviceAccess gatewayAccess(JwtUserContext user, long gatewayId) {
        if (user.companyId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Company context is required");
        }
        var access = devices.accessById(gatewayId, user.companyId(), user.normalizedRole())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gateway unavailable"));
        if (!user.companyId().equals(access.companyId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gateway unavailable");
        }
        return access;
    }

    public Page cards(JwtUserContext user, long gatewayId, long afterId, int requested) {
        var access = gatewayAccess(user, gatewayId);
        int pageSize = limit(requested);
        List<CardRow> rows = repository.cardsByGateway(access.companyId(), gatewayId,
                Math.max(0, afterId), pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        Instant now = clock.instant();
                var associations=repository.verifiedIdentities(access.companyId());
        List<Card> cards = rows.stream().limit(pageSize).map(row -> card(row, now,access.companyId(),associations)).toList();
        return new Page(cards, hasMore, hasMore ? cards.getLast().beaconId() : null,
                repository.hasUnresolvedByGateway(access.companyId(), gatewayId), now, recentThresholdSeconds);
    }

    /** Parse the durable received telemetry directly, independently of optional history tables. */
    public ReceivedPage receivedCards(JwtUserContext user, long gatewayId, long beforeId, int requested) {
        var access = gatewayAccess(user, gatewayId);
        int size = Math.max(1, Math.min(requested, 100));
        var packets = repository.receivedPackets(access.companyId(),gatewayId,
                beforeId<=0?Long.MAX_VALUE:beforeId,size+1);
        return parseReceivedPackets(access,packets,size);
    }
    ReceivedPage parseReceivedPackets(DeviceAccess access,List<ReceivedPacket> packets,int size) {
        long gatewayId=access.id();
        var associations=repository.verifiedIdentities(access.companyId());
        boolean more=packets.size()>size;
        Instant now = clock.instant();
        Map<String, ReceivedCard> cards = new LinkedHashMap<>();
        List<ReceivedError> errors = new ArrayList<>();
        for (var packet : packets.stream().limit(size).toList()) {
            TelemetryData telemetry = new TelemetryData();
            try {
                telemetry.setIoData(mapper.readValue(packet.ioJson(), new TypeReference<Map<String,Object>>() {}));
            } catch (Exception invalidJson) {
                errors.add(new ReceivedError(packet.id(), 0, "INVALID_IO_JSON", 0, packet.ioJson()));
                continue;
            }
            // These documented models define AVL385 as Manual CAN15; a BLE profile cannot override that definition.
            if (MANUAL_CAN_385_MODELS.contains(String.valueOf(packet.trackerModel()).trim().toUpperCase(java.util.Locale.ROOT))) telemetry.getIoData().remove("385");
            else if(telemetry.getIoData().get("385") instanceof String simple385
                    && !"SIMPLE_BEACON_V1".equals(captureProfiles.get(access.imei()+"/385"))) {
                telemetry.getIoData().remove("385");
                errors.add(new ReceivedError(packet.id(),385,"PENDING_TRACKER_COMPATIBILITY_VERIFICATION",0,simple385));
            }
            EyeBeaconTelemetryNormalizer.normalize(telemetry);
            List<EyeBeaconParser.Result> decoded=new ArrayList<>(telemetry.getEyeBeacons());
            String advancedLayout=captureProfiles.get(access.imei()+"/548");
            if(advancedLayout!=null && telemetry.getIoData().get("548") instanceof String advancedHex) {
                decoded.removeIf(result->result.ioId()==548);
                try{decoded.add(EyeBeaconParser.parse(548,advancedHex,advancedLayout));}
                catch(IllegalArgumentException invalid){errors.add(new ReceivedError(packet.id(),548,"INVALID_ADVANCED_LAYOUT",0,advancedHex));}
            }
            if(telemetry.getIoData().get("11317") instanceof String listHex) {
                try { decoded.add(EyeBeaconParser.parse(11317,listHex)); }
                catch(IllegalArgumentException invalidList) { errors.add(new ReceivedError(packet.id(),11317,
                        "INVALID_SENSOR_LIST",0,listHex)); }
            }
            var gps = new ReceiverGps(packet.latitude(),packet.longitude(),packet.altitude(),
                    packet.hdop(),packet.satellites(), true);
            gps = new ReceiverGps(gps.latitude(),gps.longitude(),gps.altitude(),gps.hdop(),gps.satellites(),
                    validReceiverGps(gps));
            Instant seen = packet.observedAt() == null ? packet.receivedAt() : packet.observedAt();
            for (var result : decoded) {
                for (var error : result.errors()) errors.add(new ReceivedError(packet.id(),result.ioId(),
                        error.code(),error.byteOffset(),error.rawHex()));
                int index = 0;
                for (var beacon : result.beacons()) {
                    int block = index++;
                    boolean list = result.ioId()==11317;
                    boolean verifiedListProfile=list && supportsSensorListModel(packet);
                    String key = beacon.type() + ":" + beacon.id();
                    if (!"EDDYSTONE".equals(beacon.type()) && !"IBEACON".equals(beacon.type()))
                        key = gatewayId + ":" + result.ioId() + ":" + key;
                    if(beacon.id()==null)key="UNRESOLVED:"+gatewayId+":"+packet.id()+":"+result.ioId()+":"+block;
                    var identity = new Identity(beacon.type(), beacon.id()==null?"UNRESOLVED_SENSOR_LIST":"RECEIVED_" + beacon.type(), beacon.id(),
                            beacon.namespace(),beacon.instanceId(),beacon.uuid(),
                            beacon.major() == null ? null : Integer.parseInt(beacon.major(),16),
                            beacon.minor() == null ? null : Integer.parseInt(beacon.minor(),16),list && !verifiedListProfile ? null : beacon.mac());
                    var source = new Source(null, gatewayId, access.imei(), packet.protocol(),
                            Transport.UNKNOWN,"Physical transport was not verified independently of telemetry channel.",
                            result.ioId(),block,packet.observedAt(),packet.receivedAt(),packet.id(),packet.receivedAt(),gps);
                    String ioHex = String.valueOf(telemetry.getIoData().get(Integer.toString(result.ioId())));
                    int byteOffset = Math.max(0,ioHex.toUpperCase(java.util.Locale.ROOT).indexOf(beacon.rawBlockHex())) / 2;
                    var registration = sensorInfo(identity,access.imei(),result.ioId(),access.companyId(),gatewayId,associations,beacon.otherParameters());
                    if("AMBIGUOUS".equals(registration.registrationStatus()))
                        errors.add(new ReceivedError(packet.id(),result.ioId(),"AMBIGUOUS_REGISTER_MATCH",byteOffset,beacon.rawBlockHex()));
                    Measurement parsedMeasurement;
                    if(list) parsedMeasurement=listMeasurement(packet,access.imei(),beacon,registration,errors,byteOffset);
                    else parsedMeasurement=measurement(beacon.rssiDbm(),beacon.batteryMillivolts(),beacon.temperatureRaw(),
                            beacon.rawBlockHex(),beacon.lowBattery(),beacon.otherParameters(),result.ioId(),
                            access.imei(),beacon.id(),registration,code -> errors.add(new ReceivedError(packet.id(),
                                    result.ioId(),code,byteOffset,beacon.rawBlockHex())));
                    var card = new ReceivedCard(key,beacon.type() + " " + beacon.id(),identity,seen,
                            seen.isBefore(now.minusSeconds(recentThresholdSeconds)) ? Recency.STALE : Recency.RECENT,
                            parsedMeasurement,
                            source,registration);
                    cards.put(packet.id()+":"+result.ioId()+":"+block,card);

                }
            }
            addDedicatedCards(packet,telemetry.getIoData(),gatewayId,access.imei(),seen,now,gps,cards,errors);
        }
        return new ReceivedPage(List.copyOf(cards.values()),errors,more, more ? packets.get(size-1).id() : null,now,recentThresholdSeconds);
    }

    private boolean supportsSensorListModel(ReceivedPacket packet) {
        return packet.trackerModel()!=null && java.util.Set.of("GH5200","TMT250","TFT100")
                .contains(packet.trackerModel().toUpperCase(java.util.Locale.ROOT));
    }
    private String supportedAvlProfile(ReceivedPacket packet,String imei) {
        if(!supportsSensorListModel(packet))return null;
        String profile=avlProfiles.get(imei);
        return "EYE_LIST_V203_LE".equals(profile) || "EYE_LIST_V204_BE".equals(profile) ? profile : null;
    }
    private Measurement listMeasurement(ReceivedPacket packet,String imei,EyeBeaconParser.Beacon beacon,SensorInfo registration,
            List<ReceivedError> errors,int offset) {
        String profile=supportedAvlProfile(packet,imei);
        EyeBeaconParser.SensorData data=null;
        SensorCapture capture=new SensorCapture(profile,"PENDING_FIRMWARE_VERIFICATION",
                "Tracker model, firmware and capture endian profile must be verified.");
        if(supportsSensorListModel(packet) && "PARSED".equals(registration.registrationStatus()) && "EyeSensor".equals(registration.type())) {
            if(profile==null)profile="EYE_LIST_UNVERIFIED_ENDIAN";
            try {
                data=EyeBeaconParser.decodeSensorListData(beacon.otherParameters(),profile);
                capture=new SensorCapture(profile,"DECODED","Explicit tracker firmware profile.");
            } catch(IllegalArgumentException invalid) {
                errors.add(new ReceivedError(packet.id(),11317,"INVALID_SENSOR_LIST_DATA",offset,beacon.rawBlockHex()));
                capture=new SensorCapture(profile,"INVALID_PROFILE_DATA",invalid.getMessage());
            }
        }
        return new Measurement(null,data==null?null:data.batteryMillivolts(),null,beacon.rawBlockHex(),
                data==null?null:data.lowBattery(),beacon.otherParameters(),distanceEstimator.estimate(null),data,
                data==null?null:data.temperatureCelsius(),capture);
    }
    private void addDedicatedCards(ReceivedPacket packet,Map<String,Object> io,long gateway,String imei,Instant seen,
            Instant now,ReceiverGps gps,Map<String,ReceivedCard> cards,
            List<ReceivedError> errors) {
        for(int slot=1;slot<=4;slot++) {
            int sensorSlot=slot;
            List<Integer> ids=EyeBeaconParser.dedicatedIoIds().stream().filter(id->EyeBeaconParser.dedicatedSlot(id)==sensorSlot
                    && io.containsKey(Integer.toString(id))).toList();
            if(ids.isEmpty())continue;
            String mac=slotIdentities.get(imei+"/"+slot);
            if(!supportsSensorListModel(packet) || mac==null || slotIdentities.entrySet().stream()
                    .filter(e->e.getKey().startsWith(imei+"/") && e.getValue().equals(mac)).count()!=1
                   ) {
                errors.add(new ReceivedError(packet.id(),ids.getFirst(),"PENDING_SENSOR_SLOT_VERIFICATION",0,""));continue;
            }
            var data=EyeBeaconParser.decodeDedicatedSlot(io,slot);
            var measurement=new Measurement(null,data.batteryMillivolts(),null,null,data.lowBattery(),Map.of(),
                    distanceEstimator.estimate(null),data,data.temperatureCelsius(),
                    new SensorCapture("EYE_DEDICATED_IO","DECODED","Verified configured slot; measurements belong to this packet only."));
            var identity=new Identity("UNKNOWN","VERIFIED_SLOT_MAC",mac,null,null,null,null,null,mac);
            var source=new Source(null,gateway,imei,packet.protocol(),Transport.UNKNOWN,
                    "Physical transport was not independently verified.",ids.getFirst(),slot-1,packet.observedAt(),
                    packet.receivedAt(),packet.id(),packet.receivedAt(),gps,ids);
            String key="SLOT:"+mac;
            var card=new ReceivedCard(key,"EyeSensor "+mac,identity,seen,seen.isBefore(now.minusSeconds(recentThresholdSeconds))
                    ?Recency.STALE:Recency.RECENT,measurement,source,new SensorInfo("EyeSensor "+mac,mac,"PARSED","EyeSensor"));
            var previous=cards.get(key);if(previous==null || seen.isAfter(previous.lastSeen()))cards.put(key,card);
        }
    }
    private Card card(CardRow row, Instant now, long companyId, Map<String,java.util.Set<String>> associations) {
        Instant lastSeen = row.observedAt() == null ? row.receivedAt() : row.observedAt();
        var recency = lastSeen.isBefore(now.minusSeconds(recentThresholdSeconds))
                ? EyeBeaconDtos.Recency.STALE : EyeBeaconDtos.Recency.RECENT;
        var identity = new EyeBeaconDtos.Identity(row.beaconProtocol(), row.identityKind(), row.identityValue(),
                row.namespace(), row.instanceId(), row.uuid(), row.major(), row.minor(), row.mac());
        Map<Integer,String> additional = Map.of();
        if (row.ioId() == 548 && row.rawBlockHex() != null) {
            try {
                var parsed = EyeBeaconParser.parse(548,"01" + row.rawBlockHex());
                if (parsed.beacons().size() == 1) additional = parsed.beacons().getFirst().otherParameters();
            } catch (IllegalArgumentException unsupportedHistoricalBlock) {
                // Retain raw history; an unsupported historical block is not reinterpreted.
            }
        }
        var registration = sensorInfo(identity,row.receiverImei(),row.ioId(),companyId,row.receiverDeviceId(),associations,additional);
        var measurement = measurement(row.rssiDbm(),row.batteryMillivolts(),row.temperatureRaw(),
                row.rawBlockHex(),null,additional,row.ioId(),row.receiverImei(),additional.isEmpty()?row.identityValue():historicalCapturedId(row),registration,code -> {});
        var gps = new EyeBeaconDtos.ReceiverGps(row.latitude(), row.longitude(), row.altitude(), row.hdop(),
                row.satellites(), row.gpsValid());
        var source = new EyeBeaconDtos.Source(row.observationId(), row.receiverDeviceId(), row.receiverImei(),
                row.sourceProtocol(), EyeBeaconDtos.Transport.UNKNOWN,
                "Verified physical transport was not recorded; protocol does not establish transport.",
                row.ioId(), row.blockIndex(), row.observedAt(), row.receivedAt(), row.telemetryId(),
                row.telemetryServerTime(), gps);
        return new Card(row.beaconId(), row.beaconProtocol() + " " + row.identityValue(), identity,
                lastSeen, recency, measurement, source,
                registration);
    }
    private record CaptureSelection(String profile,String error) {}
    /** Select only among explicitly configured capture layouts for this receiver/rule. */
    private CaptureSelection captureSelection(String imei,String beaconId,Map<Integer,String> additional) {
        String key=(imei+"/"+beaconId).toUpperCase(java.util.Locale.ROOT);
        String configured=captureProfiles.getOrDefault(key,captureProfiles.get(imei.toUpperCase(java.util.Locale.ROOT)+"/*"));
        if(configured==null || !configured.contains("|"))return new CaptureSelection(configured,null);
        java.util.Set<String> matches=new java.util.LinkedHashSet<>();
        for(String profile:configured.split("\\|")) {
            try {EyeBeaconParser.decodeSensorAdditionalData(profile,additional.get(2));matches.add(profile);}
            catch(IllegalArgumentException incompatibleCapture) { /* Not this approved capture layout. */ }
        }
        if(matches.size()==1)return new CaptureSelection(matches.iterator().next(),null);
        return new CaptureSelection(null,matches.isEmpty()?"NO_MATCHING_VERIFIED_CAPTURE_PROFILE":"AMBIGUOUS_CAPTURE_PROFILE");
    }

    private Measurement measurement(Integer rssi, Integer battery, Integer temperatureRaw, String raw,
            Boolean lowBattery, Map<Integer,String> additional, int ioId, String imei, String beaconId,
            SensorInfo registration, java.util.function.Consumer<String> onError) {
        EyeBeaconParser.SensorData sensor = null;
        SensorCapture capture = null;
        if (ioId == 548 && additional.containsKey(2)) {
            var selection=captureSelection(imei,beaconId,additional);
            String selected=selection.profile();
            
            boolean active="PARSED".equals(registration.registrationStatus());
            boolean isSensor="EyeSensor".equals(registration.type());
            capture=new SensorCapture(selected,"PENDING_CONFIGURATION_VERIFICATION",
                    "A verified capture profile or complete documented Teltonika advertisement is required.");
            if(selection.error()!=null) {
                capture=new SensorCapture(null,"INVALID_PROFILE_DATA",selection.error());
                onError.accept(selection.error());
            } else if(active && (isSensor || "EyeBeacon".equals(registration.type()))) {
                try {
                    if(selected==null || "FULL_AD".equals(selected)) {
                        sensor=EyeBeaconParser.decodeTeltonikaAdvertisement(additional.get(2),isSensor);
                        if(selected!=null && sensor==null)throw new IllegalArgumentException(
                                "Configured FULL_AD has no valid Teltonika manufacturer/version record");
                    } else {
                        if(isSensor!=selected.startsWith("EYE_SENSOR"))throw new IllegalArgumentException("Capture profile does not match decoded BLE type");
                        sensor=EyeBeaconParser.decodeSensorAdditionalData(selected,additional.get(2));
                    }
                    if(sensor!=null){
                        capture=new SensorCapture(sensor.profile(),"DECODED","Validated capture profile; source bytes retained.");
                        for(var error:sensor.fieldErrors().entrySet())onError.accept(error.getKey()+":"+error.getValue());
                    }
                } catch(IllegalArgumentException invalid){
                    capture=new SensorCapture(selected,"INVALID_PROFILE_DATA",invalid.getMessage());onError.accept("INVALID_EYE_CAPTURE_DATA");
                }
            }
        }
        Double celsius = sensor == null ? null : sensor.temperatureCelsius();
        // Preserve simple-mode temperatureRaw without guessing firmware-specific scaling.
        // Celsius is exposed only by the explicitly verified sensor capture profile.
        return new Measurement(rssi, battery != null ? battery : sensor == null ? null : sensor.batteryMillivolts(),
                temperatureRaw,raw,lowBattery!=null?lowBattery:sensor==null?null:sensor.lowBattery(),additional,distanceEstimator.estimate(rssi),sensor,celsius,capture,
                ioId==548?EyeBeaconParser.decodeBeaconAdvertisement(additional.get(2)):null);
    }

    /** Validate receiver GPS metadata only; no beacon position is calculated. */
    private boolean validReceiverGps(ReceiverGps gps) {
        return gps != null && gps.valid() && gps.latitude() != null && gps.longitude() != null
                && Double.isFinite(gps.latitude()) && Double.isFinite(gps.longitude())
                && Math.abs(gps.latitude()) <= 90 && Math.abs(gps.longitude()) <= 180
                && !(gps.latitude() == 0 && gps.longitude() == 0)
                && gps.satellites() != null && gps.satellites() >= 4
                && gps.hdop() != null && Double.isFinite(gps.hdop()) && gps.hdop() > 0 && gps.hdop() <= 5;
    }

    private SensorInfo sensorInfo(Identity identity,String receiverImei,
            int ioId,long companyId,long gatewayId,Map<String,java.util.Set<String>> associations,Map<Integer,String> additional) {
        java.util.Set<String> candidates=new java.util.LinkedHashSet<>();
        boolean ambiguous=false;
        addMac(candidates,identity.mac());
        String rawKey=(receiverImei+"/"+identity.value()).toUpperCase(java.util.Locale.ROOT);
        if(ioId==548 && "CUSTOM".equals(identity.protocol()) && identity.value()!=null && identity.value().matches("(?i)[0-9a-f]{12}")
                && (macIdentitySources.contains(rawKey) || macIdentitySources.contains(receiverImei.toUpperCase(java.util.Locale.ROOT)+"/*")))
            addMac(candidates,identity.value());
        java.util.Set<String> keys=new java.util.LinkedHashSet<>();
        addIdentityKeys(keys,identity.protocol(),identity.value(),identity.namespace(),identity.instanceId(),identity.uuid(),identity.major(),identity.minor(),gatewayId,ioId);
        var ad=ioId==548?EyeBeaconParser.decodeBeaconAdvertisement(additional.get(2)):null;
        if(ioId==548) {
            if(ad!=null)addIdentityKeys(keys,"EDDYSTONE_UID".equals(ad.protocol())?"EDDYSTONE":"IBEACON",null,
                    ad.namespace(),ad.instanceId(),ad.uuid(),ad.major(),ad.minor(),gatewayId,ioId);
        }
        for(String key:keys){
            candidates.addAll(associations.getOrDefault(key,java.util.Set.of()));
            String configured=identityMappings.get((companyId+"/"+receiverImei+"/"+ioId+"/"+key).toUpperCase(java.util.Locale.ROOT));
            if("AMBIGUOUS".equals(configured))ambiguous=true;
            else addMac(candidates,configured);
        }
        ambiguous=ambiguous || candidates.size()>1;
        String mac=candidates.size()==1?candidates.iterator().next():null;
        String profile=captureSelection(receiverImei,identity.value(),additional).profile();
        String type=profile!=null && profile.startsWith("EYE_SENSOR_")?"EyeSensor"
                :profile!=null && profile.startsWith("EYE_BEACON_")?"EyeBeacon"
                :ioId==11317?"EyeSensor"
                :java.util.Set.of("EDDYSTONE","IBEACON").contains(identity.protocol())?"EyeBeacon":null;
        if(type==null && ioId==548 && additional.containsKey(2)) {
            try {
                var data=EyeBeaconParser.decodeTeltonikaAdvertisement(additional.get(2),true);
                if(data!=null && (data.temperatureCelsius()!=null || data.humidityPercent()!=null || data.movementCounter()!=null
                        || data.pitchDegrees()!=null || data.rollDegrees()!=null || data.magnet()!=null))type="EyeSensor";
            } catch(IllegalArgumentException invalidAdvertisement) { /* Preserve unsupported bytes, never infer from length. */ }
        }
        String instance=identity.instanceId()!=null?identity.instanceId():ad==null?null:ad.instanceId();
        if(instance==null && "CUSTOM".equals(identity.protocol()))instance=identity.value();
        if(ambiguous && !("EyeBeacon".equals(type) && BleMacAddress.verifiedDisplayMac(instance)!=null))
            return new SensorInfo(null,null,"AMBIGUOUS",null);
        mac=BleMacAddress.displayMac(type,instance,mac);
        return new SensorInfo((type==null?"BLE":type)+" "+identity.value(),mac,"PARSED",type);
    }
    private static void addMac(java.util.Set<String> candidates,String value) {
        String mac=BleMacAddress.verifiedDisplayMac(value);
        if(mac!=null)candidates.add(mac);
    }
    private static void addIdentityKeys(java.util.Set<String> keys,String protocol,String value,String namespace,String instance,
            String uuid,Integer major,Integer minor,long gatewayId,int ioId) {
        if("EDDYSTONE".equals(protocol) && namespace!=null && instance!=null)
            keys.add("EDDYSTONE/EDDYSTONE_UID/"+namespace+":"+instance);
        if("IBEACON".equals(protocol) && uuid!=null && major!=null && minor!=null)
            keys.add("IBEACON/IBEACON_UUID_MAJOR_MINOR/"+uuid+":"+String.format("%04X:%04X",major,minor));
        if("CUSTOM".equals(protocol) && value!=null) {
            String prefix=gatewayId+":"+ioId+":";
            keys.add("CUSTOM/GATEWAY_CONFIGURED_ID/"+(value.startsWith(prefix)?value:prefix+value));
        }
    }
    public List<Map<String, Object>> latest(JwtUserContext user, long afterId, int requested) {
        return repository.latestMeasurements(user.companyId(), Math.max(0, afterId), limit(requested));
    }
}
