package com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripDetailResponse;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.DeviceLogPage;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.LatestDeviceLog;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.DeleteDeviceHistoryRequest;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.DeleteDeviceHistoryResponse;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripListResponse;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.SelectedRoutesRequest;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.SelectedRoutesResponse;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.SelectedEventsRequest;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripEvent;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.SelectedLogsRequest;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.SelectedDeviceLogPage;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripLogRow;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.PlaybackTelemetryRow;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.InstrumentSourceProfile;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.InstrumentSourceProfileList;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.InstrumentSourceOption;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.SaveInstrumentSourceProfileRequest;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.ApplyInstrumentSourceProfileRequest;
import java.util.List;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.service.DeviceWorkspaceTripRouteService;

@RestController
@RequestMapping("/api/telemetry/devices/{deviceId}/workspace/trip-route")
public class DeviceWorkspaceTripRouteController {
    private final DeviceWorkspaceTripRouteService service;
    public DeviceWorkspaceTripRouteController(DeviceWorkspaceTripRouteService service) { this.service = service; }

    @GetMapping
    public TripListResponse trips(Authentication authentication, @PathVariable Long deviceId,
                                  @RequestParam String from, @RequestParam String to) {
        return service.trips(user(authentication), deviceId, from, to);
    }

    @GetMapping("/detail")
    public TripDetailResponse detail(Authentication authentication, @PathVariable Long deviceId,
                                     @RequestParam String from, @RequestParam String to) {
        return service.detail(user(authentication), deviceId, from, to);
    }

    @PostMapping("/routes/selection")
    public SelectedRoutesResponse selectedRoutes(Authentication authentication, @PathVariable Long deviceId,
                                                   @RequestBody SelectedRoutesRequest request) {
        return service.selectedRoutes(user(authentication), deviceId, request);
    }

    @PostMapping("/events/selection")
    public List<TripEvent> selectedEvents(Authentication authentication, @PathVariable Long deviceId,
                                           @RequestBody SelectedEventsRequest request) {
        return service.selectedEvents(user(authentication), deviceId, request);
    }

    @GetMapping("/logs")
    public DeviceLogPage logs(Authentication authentication, @PathVariable Long deviceId,
                              @RequestParam String from, @RequestParam String to,
                              @RequestParam(required = false) String beforeTime,
                              @RequestParam(required = false) Long beforeId,
                              @RequestParam(required = false) Integer limit) {
        return service.logs(user(authentication), deviceId, from, to, beforeTime, beforeId, limit);
    }

    @GetMapping("/logs/latest")
    public LatestDeviceLog latestLog(Authentication authentication, @PathVariable Long deviceId,
                                     @RequestParam String from, @RequestParam String to) {
        return service.latestLog(user(authentication), deviceId, from, to);
    }

    @PostMapping("/logs/selection")
    public SelectedDeviceLogPage selectedLogs(Authentication authentication, @PathVariable Long deviceId,
                                               @RequestBody SelectedLogsRequest request) {
        return service.selectedLogs(user(authentication), deviceId, request);
    }

    @PostMapping("/logs/selection/export")
    public List<TripLogRow> selectedLogsExport(Authentication authentication, @PathVariable Long deviceId,
                                                @RequestBody SelectedLogsRequest request) {
        return service.selectedLogsExport(user(authentication), deviceId, request);
    }

    @PostMapping("/playback/telemetry")
    public List<PlaybackTelemetryRow> selectedPlaybackTelemetry(Authentication authentication, @PathVariable Long deviceId,
                                                                 @RequestBody SelectedLogsRequest request) {
        return service.selectedPlaybackTelemetry(user(authentication), deviceId, request);
    }




    @GetMapping("/instrument-source-options")
    public List<InstrumentSourceOption> instrumentSourceOptions(Authentication authentication, @PathVariable Long deviceId) {
        return service.instrumentSourceOptions(user(authentication), deviceId);
    }

    @GetMapping("/instrument-source-profiles")
    public InstrumentSourceProfileList instrumentSourceProfiles(Authentication authentication, @PathVariable Long deviceId) {
        return service.instrumentSourceProfiles(user(authentication), deviceId);
    }

    @PostMapping("/instrument-source-profiles")
    public InstrumentSourceProfile saveInstrumentSourceProfile(Authentication authentication, @PathVariable Long deviceId,
                                                               @RequestBody SaveInstrumentSourceProfileRequest request) {
        return service.saveInstrumentSourceProfile(user(authentication), deviceId, request);
    }

    @PostMapping("/instrument-source-profiles/apply")
    public InstrumentSourceProfile applyInstrumentSourceProfile(Authentication authentication, @PathVariable Long deviceId,
                                                                @RequestBody ApplyInstrumentSourceProfileRequest request) {
        return service.applyInstrumentSourceProfile(user(authentication), deviceId, request);
    }
    @DeleteMapping("/logs")
    public DeleteDeviceHistoryResponse deleteAllLogs(Authentication authentication, @PathVariable Long deviceId,
                                                       @RequestBody DeleteDeviceHistoryRequest request) {
        return service.deleteAllHistory(user(authentication), deviceId, request);
    }

    private JwtUserContext user(Authentication authentication) { return (JwtUserContext) authentication.getPrincipal(); }
}
