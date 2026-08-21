package com.alels.backend.telemetry.device.deviceworkspace.tab.logsmessage.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.telemetry.device.repository.TelemetryDeviceRepository;
import com.alels.backend.telemetry.device.repository.TelemetryDeviceRepository.DeviceAccess;
import com.alels.backend.telemetry.device.deviceworkspace.tab.logsmessage.dto.DeviceWorkspaceLogsMessageDtos.*;
import com.alels.backend.telemetry.device.deviceworkspace.tab.logsmessage.repository.DeviceWorkspaceLogsMessageRepository;

@Service
public class DeviceWorkspaceLogsMessageService {
 private final TelemetryDeviceRepository devices; private final DeviceWorkspaceLogsMessageRepository repository;
 public DeviceWorkspaceLogsMessageService(TelemetryDeviceRepository devices,DeviceWorkspaceLogsMessageRepository repository){this.devices=devices;this.repository=repository;}
 public Page<TrafficRow> traffic(JwtUserContext user,long deviceId,String from,String to,String search,String event,String transport,int page,int size){DeviceAccess d=device(user,deviceId);Range r=range(from,to);int s=size(size);int p=page(page);String q=clean(search),e=clean(event).toUpperCase(),t=clean(transport).toUpperCase();long total=repository.trafficCount(d.imei(),r.from,r.to,q,e,t);return new Page<>(repository.traffic(d.imei(),r.from,r.to,q,e,t,p,s),p,s,total,pages(total,s));}
 public Page<PacketRow> packets(JwtUserContext user,long deviceId,String from,String to,String direction,int page,int size){DeviceAccess d=device(user,deviceId);Range r=range(from,to);int s=size(size),p=page(page);String dir=clean(direction).toUpperCase();if(!dir.isEmpty()&&!dir.equals("RX")&&!dir.equals("TX"))throw bad("Direction must be RX or TX.");long total=repository.packetCount(d.imei(),r.from,r.to,dir);return new Page<>(repository.packets(d.imei(),r.from,r.to,dir,p,s),p,s,total,pages(total,s));}
 public PacketSummary summary(JwtUserContext user,long deviceId,String from,String to){DeviceAccess d=device(user,deviceId);Range r=range(from,to);return repository.summary(d.imei(),r.from,r.to);}
 public PacketDetail detail(JwtUserContext user,long deviceId,long packetId){DeviceAccess d=device(user,deviceId);return repository.detail(d.imei(),packetId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Packet was not found."));}
 private DeviceAccess device(JwtUserContext u,long id){return devices.accessById(id,u.companyId(),u.role()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Device was not found."));}
 private Range range(String f,String t){try{Instant from=Instant.parse(f),to=Instant.parse(t);if(!from.isBefore(to)||Duration.between(from,to).compareTo(Duration.ofDays(31))>0)throw bad("Date range must be positive and at most 31 days.");return new Range(from,to);}catch(ResponseStatusException e){throw e;}catch(Exception e){throw bad("Date range must use ISO-8601 timestamps.");}}
 private int size(int s){if(s!=15&&s!=25&&s!=50&&s!=75&&s!=100)throw bad("Page size must be 15, 25, 50, 75, or 100.");return s;} private int page(int p){if(p<0)throw bad("Page cannot be negative.");return p;} private int pages(long n,int s){return n==0?0:(int)Math.ceil(n/(double)s);} private String clean(String s){return s==null?"":s.trim();} private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);} private record Range(Instant from,Instant to){}
}
