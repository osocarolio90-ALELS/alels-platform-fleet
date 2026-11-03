package com.alels.backend.telemetry.ble.repository;

import java.util.List;
import java.util.Map;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.alels.backend.telemetry.ble.dto.EyeBeaconDtos.CardRow;

@Repository
public class BleObservationRepository {
    private final JdbcTemplate jdbc;
    public BleObservationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<com.alels.backend.telemetry.ble.dto.EyeBeaconDtos.ReceivedPacket> receivedPackets(
            long companyId, long gatewayId, long beforeId, int limit) {
        return jdbc.query("""
                SELECT t.id,t.server_time,t.device_time,t.protocol,t.channel,t.io_data::text AS io_json,
                       t.latitude,t.longitude,t.altitude,t.hdop,t.satellites,COALESCE(dm.model_name,d.device_model) AS tracker_model
                FROM telemetry t JOIN devices d ON d.imei=t.imei
                LEFT JOIN device_models dm ON dm.id=d.device_model_id
                WHERE d.company_id=? AND d.id=? AND t.id<?
                  AND t.io_data ??| ARRAY['10500','548','385','10828','10829','10831','11317','10800','10801','10802','10803','10804','10805','10806','10807','10808','10809','10810','10811','10812','10813','10814','10815','10816','10817','10818','10819','10820','10821','10822','10823','10824','10825','10826','10827','10832','10833','10834','10835','10836','10837','10838','10839','10840','10841','10842','10843']
                ORDER BY t.id DESC LIMIT ?
                """, (rs, n) ->
                new com.alels.backend.telemetry.ble.dto.EyeBeaconDtos.ReceivedPacket(
                    rs.getLong("id"), instant(rs,"server_time"), instant(rs,"device_time"),
                    rs.getString("protocol"),rs.getString("channel"),rs.getString("io_json"),
                    rs.getObject("latitude",Double.class),rs.getObject("longitude",Double.class),
                    rs.getObject("altitude",Integer.class),rs.getObject("hdop",Double.class),
                    rs.getObject("satellites",Integer.class),rs.getString("tracker_model")), companyId,gatewayId,beforeId,limit);
    }

    /** Existing verified associations only; discovery IDs without a verified MAC are not identity evidence. */
    public Map<String,java.util.Set<String>> verifiedIdentities(long companyId) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('public.ble_beacons') IS NOT NULL",Boolean.class)))
            return Map.of();
        Map<String,java.util.Set<String>> result=new java.util.LinkedHashMap<>();
        jdbc.query("""
                SELECT DISTINCT b.beacon_protocol,b.identity_kind,b.identity_value,b.verified_mac
                FROM ble_beacons b
                WHERE b.company_id=? AND b.verified_mac IS NOT NULL
                """,rs -> {
                    String mac=rs.getString("verified_mac").trim();
                    try { mac=com.alels.backend.telemetry.ble.service.BleMacAddress.normalizeMac(mac); }
                    catch(IllegalArgumentException invalid){return;}
                    String key=(rs.getString("beacon_protocol")+"/"+rs.getString("identity_kind")+"/"+rs.getString("identity_value")).toUpperCase(java.util.Locale.ROOT);
                    result.computeIfAbsent(key,k->new java.util.LinkedHashSet<>()).add(mac);
                },companyId);
        return result;
    }

    public List<CardRow> cardsByGateway(long companyId, long gatewayId, long afterId, int limit) {
        return jdbc.query("""
                SELECT b.id AS beacon_id, b.beacon_protocol, b.identity_kind, b.identity_value,
                       b.namespace_hex, b.instance_hex, b.uuid_hex, b.major, b.minor, b.verified_mac,
                       o.id AS observation_id, o.avl_timestamp, o.received_at, o.rssi_dbm,
                       o.battery_millivolts, o.temperature_raw, o.gateway_device_id, o.gateway_imei,
                       o.source_protocol, o.io_id, o.block_index, o.source_telemetry_id,
                       o.source_telemetry_server_time, o.gateway_latitude, o.gateway_longitude,
                       o.gateway_altitude, o.gateway_hdop, o.gateway_satellites, o.gps_valid, o.raw_block_hex
                FROM ble_beacons b
                JOIN LATERAL (
                    SELECT history.* FROM ble_observations history
                    WHERE history.company_id = b.company_id AND history.beacon_id = b.id
                      AND history.gateway_device_id = ? AND history.parser_status = 'VALID'
                    ORDER BY COALESCE(history.avl_timestamp, history.received_at) DESC,
                             history.received_at DESC, history.id DESC LIMIT 1
                ) o ON TRUE
                WHERE b.company_id = ? AND b.id > ?
                ORDER BY b.id LIMIT ?
                """, (rs, rowNum) -> new CardRow(rs.getLong("beacon_id"), rs.getString("beacon_protocol"),
                rs.getString("identity_kind"), rs.getString("identity_value"), trim(rs.getString("namespace_hex")),
                trim(rs.getString("instance_hex")), trim(rs.getString("uuid_hex")),
                rs.getObject("major", Integer.class), rs.getObject("minor", Integer.class),
                trim(rs.getString("verified_mac")), rs.getLong("observation_id"), instant(rs, "avl_timestamp"),
                instant(rs, "received_at"), rs.getObject("rssi_dbm", Integer.class),
                rs.getObject("battery_millivolts", Integer.class), rs.getObject("temperature_raw", Integer.class),
                rs.getLong("gateway_device_id"), rs.getString("gateway_imei"), rs.getString("source_protocol"),
                rs.getInt("io_id"), rs.getInt("block_index"), rs.getLong("source_telemetry_id"),
                instant(rs, "source_telemetry_server_time"), rs.getObject("gateway_latitude", Double.class),
                rs.getObject("gateway_longitude", Double.class), rs.getObject("gateway_altitude", Integer.class),
                rs.getObject("gateway_hdop", Double.class), rs.getObject("gateway_satellites", Integer.class),
                rs.getBoolean("gps_valid"), rs.getString("raw_block_hex")), gatewayId, companyId, afterId, limit);
    }

    public boolean hasUnresolvedByGateway(long companyId, long gatewayId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM ble_observations
                    WHERE company_id = ? AND gateway_device_id = ? AND beacon_id IS NULL)
                """, Boolean.class, companyId, gatewayId));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String trim(String value) { return value == null ? null : value.trim(); }

    public List<Map<String, Object>> beacons(long companyId, long afterId, int limit) {
        return jdbc.queryForList("""
                SELECT id, beacon_protocol, identity_kind, identity_value, namespace_hex,
                       instance_hex, uuid_hex, major, minor, verified_mac, first_seen, last_seen
                FROM ble_beacons WHERE company_id = ? AND id > ? ORDER BY id LIMIT ?
                """, companyId, afterId, limit);
    }

    public List<Map<String, Object>> byBeacon(long companyId, long beaconId, long beforeId, int limit) {
        return jdbc.queryForList("""
                SELECT o.id, o.gateway_device_id, o.gateway_imei, o.source_telemetry_id,
                       o.source_telemetry_server_time, o.source_protocol, o.channel,
                       o.avl_timestamp, o.received_at, o.io_id, o.block_index,
                       o.beacon_protocol, o.raw_identifier, o.rssi_dbm, o.battery_millivolts,
                       o.temperature_raw, o.gateway_latitude, o.gateway_longitude,
                       o.gateway_altitude, o.gateway_hdop, o.gateway_satellites, o.gps_valid,
                       o.raw_block_hex, o.parser_status, o.parser_error
                FROM ble_observations o WHERE o.company_id = ? AND o.beacon_id = ? AND o.id < ?
                ORDER BY o.id DESC LIMIT ?
                """, companyId, beaconId, beforeId, limit);
    }

    public List<Map<String, Object>> byGateway(long companyId, long gatewayId, long beforeId, int limit) {
        return jdbc.queryForList("""
                SELECT id, beacon_id, source_telemetry_id, source_telemetry_server_time,
                       source_protocol, channel, avl_timestamp, received_at, io_id, block_index,
                       beacon_protocol, raw_identifier, rssi_dbm, battery_millivolts,
                       temperature_raw, gps_valid, raw_block_hex, parser_status, parser_error
                FROM ble_observations WHERE company_id = ? AND gateway_device_id = ? AND id < ?
                ORDER BY id DESC LIMIT ?
                """, companyId, gatewayId, beforeId, limit);
    }

    public List<Map<String, Object>> latestMeasurements(long companyId, long afterId, int limit) {
        return jdbc.queryForList("""
                SELECT b.id AS beacon_id, b.identity_value, b.last_seen,
                       o.gateway_device_id, o.gateway_imei, o.avl_timestamp,
                       o.rssi_dbm, o.battery_millivolts, o.temperature_raw
                FROM ble_beacons b
                LEFT JOIN LATERAL (
                    SELECT gateway_device_id, gateway_imei, avl_timestamp, rssi_dbm,
                           battery_millivolts, temperature_raw
                    FROM ble_observations
                    WHERE company_id = b.company_id AND beacon_id = b.id AND parser_status = 'VALID'
                    ORDER BY avl_timestamp DESC NULLS LAST, received_at DESC, id DESC LIMIT 1
                ) o ON TRUE
                WHERE b.company_id = ? AND b.id > ? ORDER BY b.id LIMIT ?
                """, companyId, afterId, limit);
    }
    public record PendingDecodedPacket(long companyId,long gatewayId,String imei,long sequence,String raw,
            com.alels.backend.telemetry.ble.dto.EyeBeaconDtos.ReceivedPacket packet) {}

    public boolean decodedStorageAvailable() {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM information_schema.columns
                WHERE table_schema='public' AND table_name='ble_observations' AND column_name='decoded_payload')
                """,Boolean.class));
    }

    /** Bounded durable backlog; failed transactions remain eligible for retry without touching ingestion. */
    public List<PendingDecodedPacket> pendingDecodedPackets(int limit,String configuration) {
        return jdbc.query("""
                WITH candidates AS MATERIALIZED (
                SELECT t.id,t.imei,t.packet_sequence,t.server_time,t.device_time,t.protocol,t.channel,
                       t.io_data::text io_json,t.latitude,t.longitude,t.altitude,t.hdop,t.satellites,
                       d.id gateway_id,d.company_id,COALESCE(dm.model_name,d.device_model) tracker_model,
                       COALESCE(r.raw_hex,r.raw_json,r.payload) source_raw,
                       alels_ble_source_key(t.imei,t.protocol,t.channel,t.packet_sequence,
                           COALESCE(r.raw_hex,r.raw_json,r.payload)) source_key
                FROM telemetry t JOIN devices d ON d.imei=t.imei AND d.deleted_at IS NULL
                LEFT JOIN device_models dm ON dm.id=d.device_model_id
                LEFT JOIN LATERAL (SELECT raw_hex,raw_json,payload FROM raw_packets
                    WHERE id=t.raw_packet_id AND imei=t.imei ORDER BY received_at DESC LIMIT 1) r ON TRUE
                WHERE d.company_id IS NOT NULL AND t.io_data ??| ARRAY['548','10500']
                  AND NOT EXISTS(SELECT 1 FROM ble_observations o WHERE o.company_id=d.company_id
                    AND o.source_telemetry_id=t.id AND o.source_telemetry_server_time=t.server_time AND o.decoder_revision=1
                    AND COALESCE(o.decoded_payload->>'_decoderConfiguration',o.parser_error->>'_decoderConfiguration','')=?)
                )
                SELECT c.* FROM candidates c
                WHERE NOT EXISTS(SELECT 1 FROM ble_observations o WHERE o.company_id=c.company_id
                    AND o.source_record_key=c.source_key AND ((o.decoder_revision=1
                        AND COALESCE(o.decoded_payload->>'_decoderConfiguration',o.parser_error->>'_decoderConfiguration','')=?)
                        OR o.source_telemetry_id<>c.id OR o.source_telemetry_server_time<>c.server_time))
                ORDER BY c.id DESC,c.server_time DESC LIMIT ?
                """,(rs,n)->new PendingDecodedPacket(rs.getLong("company_id"),rs.getLong("gateway_id"),rs.getString("imei"),
                    rs.getLong("packet_sequence"),rs.getString("source_raw"),
                    new com.alels.backend.telemetry.ble.dto.EyeBeaconDtos.ReceivedPacket(rs.getLong("id"),instant(rs,"server_time"),instant(rs,"device_time"),
                        rs.getString("protocol"),rs.getString("channel"),rs.getString("io_json"),rs.getObject("latitude",Double.class),
                        rs.getObject("longitude",Double.class),rs.getObject("altitude",Integer.class),rs.getObject("hdop",Double.class),
                        rs.getObject("satellites",Integer.class),rs.getString("tracker_model"))),configuration,configuration,Math.max(1,Math.min(limit,100)));
    }

    public void storeDecodedPacket(PendingDecodedPacket pending,
            com.alels.backend.telemetry.ble.dto.EyeBeaconDtos.ReceivedPage page,com.fasterxml.jackson.databind.ObjectMapper mapper,String configuration) {
        var transaction=new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(java.util.Objects.requireNonNull(jdbc.getDataSource())));
        transaction.executeWithoutResult(status->{
            var packet=pending.packet();
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)",Object.class,packet.id());
            try {
                String raw=pending.raw();
                if(raw==null)throw new IllegalStateException("Original raw packet is required for durable BLE provenance");
                String sourceKey=jdbc.queryForObject("SELECT alels_ble_source_key(?,?,?,?,?)",String.class,
                        pending.imei(),packet.protocol(),packet.channel(),pending.sequence(),raw);
                var existing=jdbc.queryForList("SELECT source_record_key FROM ble_observations WHERE company_id=? AND source_telemetry_id=? AND source_telemetry_server_time=? ORDER BY id LIMIT 1",
                        String.class,pending.companyId(),packet.id(),java.sql.Timestamp.from(packet.receivedAt()));
                if(!existing.isEmpty())sourceKey=existing.getFirst();
                var io=mapper.readValue(packet.ioJson(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
                var results=new java.util.ArrayList<com.alels.gateway.parser.EyeBeaconParser.Result>();
                for(int id:new int[]{548,10500}) {
                    if(!io.containsKey(Integer.toString(id)))continue;
                    var blocks=page.beacons().stream().filter(c->c.source().ioId()==id).toList();
                    var failures=page.errors().stream().filter(e->e.ioId()==id).map(e->
                        new com.alels.gateway.parser.EyeBeaconParser.ParseError(e.code(),e.byteOffset(),e.rawHex())).toList();
                    var beacons=new java.util.ArrayList<com.alels.gateway.parser.EyeBeaconParser.Beacon>();
                    for(var card:blocks) {
                        var m=card.measurement();var identity=card.identity();
                        beacons.add(new com.alels.gateway.parser.EyeBeaconParser.Beacon(identity.protocol(),identity.value(),m.rssiDbm(),
                            m.lowBattery(),m.batteryMillivolts(),identity.mac(),m.otherParameters(),m.temperatureRaw(),m.rawBlockHex()));
                    }
                    if(beacons.isEmpty() && failures.isEmpty())failures=List.of(new com.alels.gateway.parser.EyeBeaconParser.ParseError("EMPTY_BLE_PAYLOAD",0,String.valueOf(io.get(Integer.toString(id)))));
                    results.add(new com.alels.gateway.parser.EyeBeaconParser.Result(id,1,1,beacons,
                        failures.isEmpty()?"":String.valueOf(io.get(Integer.toString(id))),failures));
                }
                jdbc.queryForObject("SELECT alels_store_ble_observations(?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb)",Integer.class,
                    pending.imei(),sourceKey,packet.id(),java.sql.Timestamp.from(packet.receivedAt()),packet.protocol(),packet.channel(),
                    packet.observedAt()==null?null:java.sql.Timestamp.from(packet.observedAt()),java.sql.Timestamp.from(packet.receivedAt()),
                    packet.latitude(),packet.longitude(),packet.altitude(),packet.hdop(),packet.satellites(),mapper.writeValueAsString(results));
                for(var card:page.beacons()) {
                    if(card.source().ioId()!=548 && card.source().ioId()!=10500)continue;
                    boolean partial=page.errors().stream().anyMatch(e->e.ioId()==card.source().ioId());
                    var snapshot=mapper.<com.fasterxml.jackson.databind.node.ObjectNode>valueToTree(card);
                    snapshot.put("_decoderConfiguration",configuration);
                    jdbc.update("""
                        UPDATE ble_observations SET decoded_payload=?::jsonb,decoder_revision=1,decoded_at=clock_timestamp(),
                          battery_millivolts=?,parser_status=CASE WHEN ? THEN 'PARTIAL' ELSE 'VALID' END
                        WHERE company_id=? AND source_telemetry_id=? AND source_telemetry_server_time=? AND io_id=? AND block_index=?
                        """,mapper.writeValueAsString(snapshot),card.measurement().batteryMillivolts(),partial,pending.companyId(),packet.id(),
                            java.sql.Timestamp.from(packet.receivedAt()),card.source().ioId(),card.source().blockIndex());
                }
                jdbc.update("UPDATE ble_observations SET decoder_revision=1,decoded_at=clock_timestamp(),parser_error=CASE WHEN decoded_payload IS NULL THEN COALESCE(parser_error,'{}'::jsonb)||jsonb_build_object('_decoderConfiguration',?::text) ELSE parser_error END WHERE company_id=? AND source_telemetry_id=? AND source_telemetry_server_time=? AND io_id IN(548,10500)",
                    configuration,pending.companyId(),packet.id(),java.sql.Timestamp.from(packet.receivedAt()));
            } catch(Exception failure) {throw new IllegalStateException("BLE decoded persistence failed for telemetry "+packet.id(),failure);}
        });
    }

}
