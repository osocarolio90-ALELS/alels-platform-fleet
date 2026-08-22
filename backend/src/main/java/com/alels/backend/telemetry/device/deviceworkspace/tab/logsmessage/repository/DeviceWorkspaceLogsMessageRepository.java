package com.alels.backend.telemetry.device.deviceworkspace.tab.logsmessage.repository;

import java.time.Instant;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.alels.backend.telemetry.device.deviceworkspace.tab.logsmessage.dto.DeviceWorkspaceLogsMessageDtos.*;

@Repository
public class DeviceWorkspaceLogsMessageRepository {
    private final JdbcTemplate jdbc;
    public DeviceWorkspaceLogsMessageRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long trafficCount(String imei, Instant from, Instant to, String search, String event, String transport) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tcp_logs WHERE imei=? AND created_at BETWEEN ? AND ? AND (?='' OR event_type=?) AND (?='' OR UPPER(channel)=?) AND (?='' OR LOWER(COALESCE(message,'')||' '||COALESCE(remote_address,'')||' '||COALESCE(event_type,'')) LIKE '%'||LOWER(?)||'%')",
                Long.class, imei, timestamp(from), timestamp(to), event, event, transport, transport, search, search);
    }
    public List<TrafficRow> traffic(String imei, Instant from, Instant to, String search, String event, String transport, int page, int size) {
        return jdbc.query("SELECT id,created_at,event_type,imei,bytes_in,bytes_out,remote_address,channel,protocol,message FROM tcp_logs WHERE imei=? AND created_at BETWEEN ? AND ? AND (?='' OR event_type=?) AND (?='' OR UPPER(channel)=?) AND (?='' OR LOWER(COALESCE(message,'')||' '||COALESCE(remote_address,'')||' '||COALESCE(event_type,'')) LIKE '%'||LOWER(?)||'%') ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs,n)->new TrafficRow(rs.getLong("id"),rs.getTimestamp("created_at").toInstant().toString(),rs.getString("event_type"),title(rs.getString("event_type")),rs.getString("imei"),nullableLong(rs,"bytes_in"),nullableLong(rs,"bytes_out"),rs.getString("remote_address"),upper(rs.getString("channel")),source(rs.getString("protocol")),protocol(rs.getString("protocol")),status(rs.getString("event_type")),rs.getString("message")),
                imei,timestamp(from),timestamp(to),event,event,transport,transport,search,search,size,(long)page*size);
    }
    public long packetCount(String imei, Instant from, Instant to, String direction) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM raw_packets WHERE imei=? AND received_at BETWEEN ? AND ? AND (?='' OR CASE WHEN UPPER(COALESCE(direction,'')) IN ('TX','OUT','OUTBOUND','SERVER_TO_DEVICE') THEN 'TX' ELSE 'RX' END=?)",Long.class,imei,timestamp(from),timestamp(to),direction,direction);
    }
    public List<PacketRow> packets(String imei, Instant from, Instant to, String direction, int page, int size) {
        return jdbc.query("SELECT id,received_at,direction,COALESCE(bytes_count,0) size_bytes,channel,protocol FROM raw_packets WHERE imei=? AND received_at BETWEEN ? AND ? AND (?='' OR CASE WHEN UPPER(COALESCE(direction,'')) IN ('TX','OUT','OUTBOUND','SERVER_TO_DEVICE') THEN 'TX' ELSE 'RX' END=?) ORDER BY received_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs,n)->new PacketRow(rs.getLong("id"),rs.getTimestamp("received_at").toInstant().toString(),direction(rs.getString("direction")),rs.getLong("size_bytes"),upper(rs.getString("channel")),protocol(rs.getString("protocol"))),imei,timestamp(from),timestamp(to),direction,direction,size,(long)page*size);
    }
    public Optional<PacketDetail> detail(String imei, long id) {
        return jdbc.query("SELECT id,received_at,direction,COALESCE(bytes_count,0) size_bytes,channel,protocol,remote_address,raw_hex,COALESCE(raw_json,payload) raw_text FROM raw_packets WHERE imei=? AND id=?",
                (rs,n)->new PacketDetail(rs.getLong("id"),rs.getTimestamp("received_at").toInstant().toString(),direction(rs.getString("direction")),rs.getLong("size_bytes"),upper(rs.getString("channel")),protocol(rs.getString("protocol")),rs.getString("remote_address"),rs.getString("raw_hex"),rawText(rs.getString("raw_text"),rs.getString("raw_hex"))),imei,id).stream().findFirst();
    }
    public PacketSummary summary(String imei, Instant from, Instant to) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN UPPER(COALESCE(direction,'')) IN ('TX','OUT','OUTBOUND','SERVER_TO_DEVICE') THEN 0 ELSE COALESCE(bytes_count,0) END),0) rx_bytes,COALESCE(SUM(CASE WHEN UPPER(COALESCE(direction,'')) IN ('TX','OUT','OUTBOUND','SERVER_TO_DEVICE') THEN COALESCE(bytes_count,0) ELSE 0 END),0) tx_bytes,COUNT(*) FILTER (WHERE UPPER(COALESCE(direction,'')) NOT IN ('TX','OUT','OUTBOUND','SERVER_TO_DEVICE')) rx_count,COUNT(*) FILTER (WHERE UPPER(COALESCE(direction,'')) IN ('TX','OUT','OUTBOUND','SERVER_TO_DEVICE')) tx_count FROM raw_packets WHERE imei=? AND received_at BETWEEN ? AND ?",
                (rs,n)->new PacketSummary(rs.getLong("rx_bytes"),rs.getLong("tx_bytes"),rs.getLong("rx_bytes")+rs.getLong("tx_bytes"),rs.getLong("rx_count"),rs.getLong("tx_count")),imei,timestamp(from),timestamp(to));
    }
    private static String direction(String value){return value!=null&&List.of("TX","OUT","OUTBOUND","SERVER_TO_DEVICE").contains(value.toUpperCase())?"TX":"RX";}
    private static String upper(String value){return value==null?null:value.toUpperCase();}
    private static String protocol(String value){String normalized=upper(value);if(normalized==null)return null;if(normalized.contains("8E"))return "CODEC8E";if(normalized.contains("CODEC8"))return "CODEC8";if(normalized.contains("ALELS"))return "ALELS";return normalized;}
    private static String source(String protocol){String normalized=upper(protocol);if(normalized==null)return null;if(normalized.contains("ALELS"))return "WIFI ALELS";if(normalized.contains("TELTONIKA")||normalized.contains("CODEC"))return "GSM";return null;}
    private static String title(String value){if(value==null||value.isBlank())return "Connection event";String[] words=value.toLowerCase().split("_");String joined=String.join(" ",words);return Character.toUpperCase(joined.charAt(0))+joined.substring(1);}
    private static String status(String value){String v=upper(value);if(v!=null&&(v.contains("ERROR")||v.contains("FAIL")))return "ERROR";if(v!=null&&v.contains("BLOCK"))return "BLOCKED";return "SUCCESS";}
    private static Long nullableLong(ResultSet resultSet,String column) throws SQLException {long value=resultSet.getLong(column);return resultSet.wasNull()?null:value;}
    private static String rawText(String stored,String hex){if(stored!=null)return stored;if(hex==null||hex.isBlank())return null;try{byte[] bytes=java.util.HexFormat.of().parseHex(hex);String text=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);for(int i=0;i<text.length();i++){char value=text.charAt(i);if(value=='\r'||value=='\n'||value=='\t')continue;if(Character.isISOControl(value)||value=='\uFFFD')return "HEX: "+hex;}return text;}catch(IllegalArgumentException error){return "HEX: "+hex;}}
    private static Timestamp timestamp(Instant value){return Timestamp.from(value);}
}
