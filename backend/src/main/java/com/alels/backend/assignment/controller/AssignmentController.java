package com.alels.backend.assignment.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alels.backend.assignment.dto.AssignmentDtos.AssetAssignmentRequest;
import com.alels.backend.assignment.dto.AssignmentDtos.AssetAssignmentRow;
import com.alels.backend.assignment.dto.AssignmentDtos.AssignmentLookupOption;
import com.alels.backend.assignment.dto.AssignmentDtos.DriverManualAssignmentRequest;
import com.alels.backend.assignment.dto.AssignmentDtos.DriverManualAssignmentRow;
import com.alels.backend.assignment.dto.AssignmentDtos.VehicleDeviceAssignmentRequest;
import com.alels.backend.assignment.dto.AssignmentDtos.VehicleDeviceAssignmentRow;
import com.alels.backend.assignment.service.AssignmentService;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@RestController
@RequestMapping("/api/assignment")
public class AssignmentController {
    private final AssignmentService service;

    public AssignmentController(AssignmentService service) {
        this.service = service;
    }

    @GetMapping("/assets")
    public List<AssetAssignmentRow> assetAssignmentList(Authentication authentication) {
        return service.assetAssignmentList(user(authentication));
    }

    @PostMapping("/assets")
    public Map<String, Object> createAssetAssignment(Authentication authentication, @RequestBody AssetAssignmentRequest request) {
        Long id = service.createAssetAssignment(user(authentication), request);
        return Map.of("success", true, "id", id);
    }

    @PutMapping("/assets/{id}")
    public Map<String, Object> updateAssetAssignment(Authentication authentication, @PathVariable Long id, @RequestBody AssetAssignmentRequest request) {
        service.updateAssetAssignment(user(authentication), id, request);
        return Map.of("success", true);
    }

    @PostMapping("/assets/{id}/unpair-vehicle")
    public Map<String, Object> unpairAssetVehicle(Authentication authentication, @PathVariable Long id) {
        service.unpairAssetVehicle(user(authentication), id);
        return Map.of("success", true);
    }

    @PostMapping("/assets/{id}/unpair-device")
    public Map<String, Object> unpairAssetDevice(Authentication authentication, @PathVariable Long id) {
        service.unpairAssetDevice(user(authentication), id);
        return Map.of("success", true);
    }

    @PostMapping("/assets/{id}/unpair-driver")
    public Map<String, Object> unpairAssetDriver(Authentication authentication, @PathVariable Long id) {
        service.unpairAssetDriver(user(authentication), id);
        return Map.of("success", true);
    }

    @GetMapping("/vehicle-device")
    public List<VehicleDeviceAssignmentRow> vehicleDeviceList(Authentication authentication) {
        return service.vehicleDeviceList(user(authentication));
    }

    @PostMapping("/vehicle-device")
    public Map<String, Object> createVehicleDevice(Authentication authentication, @RequestBody VehicleDeviceAssignmentRequest request) {
        Long id = service.createVehicleDevice(user(authentication), request);
        return Map.of("success", true, "id", id);
    }

    @PutMapping("/vehicle-device/{id}")
    public Map<String, Object> updateVehicleDevice(Authentication authentication, @PathVariable Long id, @RequestBody VehicleDeviceAssignmentRequest request) {
        service.updateVehicleDevice(user(authentication), id, request);
        return Map.of("success", true);
    }

    @PostMapping("/vehicle-device/{id}/remove")
    public Map<String, Object> removeVehicleDevice(Authentication authentication, @PathVariable Long id) {
        service.removeVehicleDevice(user(authentication), id);
        return Map.of("success", true);
    }

    @GetMapping("/driver-manual")
    public List<DriverManualAssignmentRow> driverManualList(Authentication authentication) {
        return service.driverManualList(user(authentication));
    }

    @PostMapping("/driver-manual")
    public Map<String, Object> createDriverManual(Authentication authentication, @RequestBody DriverManualAssignmentRequest request) {
        Long id = service.createDriverManual(user(authentication), request);
        return Map.of("success", true, "id", id);
    }

    @PutMapping("/driver-manual/{id}")
    public Map<String, Object> updateDriverManual(Authentication authentication, @PathVariable Long id, @RequestBody DriverManualAssignmentRequest request) {
        service.updateDriverManual(user(authentication), id, request);
        return Map.of("success", true);
    }

    @PostMapping("/driver-manual/{id}/remove")
    public Map<String, Object> removeDriverManual(Authentication authentication, @PathVariable Long id) {
        service.removeDriverManual(user(authentication), id);
        return Map.of("success", true);
    }

    @GetMapping("/options/companies")
    public List<AssignmentLookupOption> companyOptions(Authentication authentication) {
        return service.companyOptions(user(authentication));
    }

    @GetMapping("/options/vehicles")
    public List<AssignmentLookupOption> vehicleOptions(Authentication authentication) {
        return service.vehicleOptions(user(authentication));
    }

    @GetMapping("/options/devices")
    public List<AssignmentLookupOption> deviceOptions(Authentication authentication) {
        return service.deviceOptions(user(authentication));
    }

    @GetMapping("/options/drivers")
    public List<AssignmentLookupOption> driverOptions(Authentication authentication) {
        return service.driverOptions(user(authentication));
    }

    private JwtUserContext user(Authentication authentication) {
        return (JwtUserContext) authentication.getPrincipal();
    }
}
