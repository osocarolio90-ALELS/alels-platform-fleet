package com.alels.backend.telemetry.group.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.telemetry.group.dto.TelemetryGroupDtos.*;
import com.alels.backend.telemetry.group.service.TelemetryGroupService;

@RestController @RequestMapping("/api/telemetry/groups")
public class TelemetryGroupController {
    private final TelemetryGroupService service;
    public TelemetryGroupController(TelemetryGroupService service){this.service=service;}
    @GetMapping public List<GroupRow> list(Authentication a){return service.list(user(a));}
    @GetMapping("/logs") public List<LogRow> logs(Authentication a){return service.logs(user(a));}
    @GetMapping("/wasted") public List<GroupRow> wasted(Authentication a){return service.wasted(user(a));}
    @GetMapping("/devices") public List<DeviceOption> devices(Authentication a,@RequestParam(required=false) Long currentGroupId){return service.devices(user(a),currentGroupId);}
    @PostMapping public Map<String,Object> create(Authentication a,@RequestBody GroupRequest r){return Map.of("success",true,"id",service.create(user(a),r));}
    @PutMapping("/{id}") public Map<String,Object> update(Authentication a,@PathVariable Long id,@RequestBody GroupRequest r){service.update(user(a),id,r);return Map.of("success",true);}
    @DeleteMapping("/{id}") public Map<String,Object> delete(Authentication a,@PathVariable Long id){service.delete(user(a),id);return Map.of("success",true);}
    @PostMapping("/{id}/restore") public Map<String,Object> restore(Authentication a,@PathVariable Long id){service.restore(user(a),id);return Map.of("success",true);}
    @DeleteMapping("/{id}/permanent") public Map<String,Object> permanent(Authentication a,@PathVariable Long id){service.permanentDelete(user(a),id);return Map.of("success",true);}
    private JwtUserContext user(Authentication a){return (JwtUserContext)a.getPrincipal();}
}
