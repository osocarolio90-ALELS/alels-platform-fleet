package com.alels.backend.telemetry.assignment.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.telemetry.assignment.dto.AssignmentDtos.AssetAssignmentRequest;
import com.alels.backend.telemetry.assignment.dto.AssignmentDtos.AssetAssignmentRow;
import com.alels.backend.telemetry.assignment.dto.AssignmentDtos.AssignmentLookupOption;
import com.alels.backend.telemetry.assignment.dto.AssignmentDtos.DriverManualAssignmentRequest;
import com.alels.backend.telemetry.assignment.dto.AssignmentDtos.DriverManualAssignmentRow;
import com.alels.backend.telemetry.assignment.dto.AssignmentDtos.VehicleDeviceAssignmentRequest;
import com.alels.backend.telemetry.assignment.dto.AssignmentDtos.VehicleDeviceAssignmentRow;
import com.alels.backend.telemetry.assignment.repository.AssignmentRepository;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@Service
public class AssignmentService {
    private final AssignmentRepository repository;

    public AssignmentService(AssignmentRepository repository) {
        this.repository = repository;
    }

    public List<AssetAssignmentRow> assetAssignmentList(JwtUserContext user) {
        return repository.assetAssignmentList(user.companyId(), user.normalizedRole());
    }

    @Transactional
    public Long createAssetAssignment(JwtUserContext user, AssetAssignmentRequest request) {
        Long effectiveCompanyId = resolveVehicleDeviceCompany(user, request.companyId(), request.vehicleId(), request.deviceId());
        if (request.driverId() != null) {
            validateDriverForCompany(user, request.driverId(), effectiveCompanyId);
        }
        VehicleDeviceAssignmentRequest vehicleDeviceRequest = new VehicleDeviceAssignmentRequest(effectiveCompanyId, request.vehicleId(), request.deviceId(), request.notes());
        Long id = repository.createVehicleDevice(vehicleDeviceRequest, user.userId());
        repository.log(user.userId(), user.companyId(), "ASSET_ASSIGNMENT", id, "ASSET_PAIR");
        if (request.driverId() != null) {
            DriverManualAssignmentRequest driverRequest = new DriverManualAssignmentRequest(effectiveCompanyId, request.vehicleId(), request.deviceId(), request.driverId(), request.notes());
            Long driverAssignmentId = repository.createDriverManual(driverRequest, user.userId());
            repository.log(user.userId(), user.companyId(), "DRIVER_MANUAL_ASSIGNMENT", driverAssignmentId, "DRIVER_MANUAL_ASSIGN");
        }
        return id;
    }

    @Transactional
    public void updateAssetAssignment(JwtUserContext user, Long id, AssetAssignmentRequest request) {
        assertAssignmentAccess(user, "vehicle_device_assignments", id);
        Long effectiveCompanyId = resolveVehicleDeviceCompany(user, request.companyId(), request.vehicleId(), request.deviceId());
        if (request.driverId() != null) {
            validateDriverForCompany(user, request.driverId(), effectiveCompanyId);
        }
        VehicleDeviceAssignmentRequest vehicleDeviceRequest = new VehicleDeviceAssignmentRequest(effectiveCompanyId, request.vehicleId(), request.deviceId(), request.notes());
        repository.updateVehicleDevice(id, vehicleDeviceRequest, user.userId());
        repository.log(user.userId(), user.companyId(), "ASSET_ASSIGNMENT", id, "ASSET_PAIR_UPDATE");

        repository.activeDriverManualIdByVehicleDevice(request.vehicleId(), request.deviceId()).ifPresent(driverAssignmentId -> {
            if (request.driverId() == null) {
                repository.removeDriverManual(driverAssignmentId, user.userId());
                repository.log(user.userId(), user.companyId(), "DRIVER_MANUAL_ASSIGNMENT", driverAssignmentId, "DRIVER_MANUAL_REMOVE");
            }
        });
        if (request.driverId() != null) {
            DriverManualAssignmentRequest driverRequest = new DriverManualAssignmentRequest(effectiveCompanyId, request.vehicleId(), request.deviceId(), request.driverId(), request.notes());
            var currentDriverAssignmentId = repository.activeDriverManualIdByVehicleDevice(request.vehicleId(), request.deviceId());
            if (currentDriverAssignmentId.isPresent()) {
                repository.updateDriverManual(currentDriverAssignmentId.get(), driverRequest, user.userId());
                repository.log(user.userId(), user.companyId(), "DRIVER_MANUAL_ASSIGNMENT", currentDriverAssignmentId.get(), "DRIVER_MANUAL_UPDATE");
            } else {
                Long driverAssignmentId = repository.createDriverManual(driverRequest, user.userId());
                repository.log(user.userId(), user.companyId(), "DRIVER_MANUAL_ASSIGNMENT", driverAssignmentId, "DRIVER_MANUAL_ASSIGN");
            }
        }
    }

    public void unpairAssetVehicle(JwtUserContext user, Long id) {
        removeVehicleDevice(user, id);
    }

    public void unpairAssetDevice(JwtUserContext user, Long id) {
        removeVehicleDevice(user, id);
    }

    public void unpairAssetDriver(JwtUserContext user, Long id) {
        assertAssignmentAccess(user, "vehicle_device_assignments", id);
        AssetAssignmentRow row = repository.assetAssignmentList(user.companyId(), user.normalizedRole()).stream()
                .filter(item -> id.equals(item.id()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset assignment tidak ditemukan."));
        if (row.driverAssignmentId() == null) return;
        repository.removeDriverManual(row.driverAssignmentId(), user.userId());
        repository.log(user.userId(), user.companyId(), "DRIVER_MANUAL_ASSIGNMENT", row.driverAssignmentId(), "DRIVER_MANUAL_REMOVE");
    }

    public List<VehicleDeviceAssignmentRow> vehicleDeviceList(JwtUserContext user) {
        return repository.vehicleDeviceList(user.companyId(), user.normalizedRole());
    }

    public Long createVehicleDevice(JwtUserContext user, VehicleDeviceAssignmentRequest request) {
        Long effectiveCompanyId = resolveVehicleDeviceCompany(user, request.companyId(), request.vehicleId(), request.deviceId());
        VehicleDeviceAssignmentRequest normalizedRequest = new VehicleDeviceAssignmentRequest(effectiveCompanyId, request.vehicleId(), request.deviceId(), request.notes());
        Long id = repository.createVehicleDevice(normalizedRequest, user.userId());
        repository.log(user.userId(), user.companyId(), "VEHICLE_DEVICE_ASSIGNMENT", id, "VEHICLE_DEVICE_ASSIGN");
        return id;
    }

    public void updateVehicleDevice(JwtUserContext user, Long id, VehicleDeviceAssignmentRequest request) {
        assertAssignmentAccess(user, "vehicle_device_assignments", id);
        Long effectiveCompanyId = resolveVehicleDeviceCompany(user, request.companyId(), request.vehicleId(), request.deviceId());
        VehicleDeviceAssignmentRequest normalizedRequest = new VehicleDeviceAssignmentRequest(effectiveCompanyId, request.vehicleId(), request.deviceId(), request.notes());
        repository.updateVehicleDevice(id, normalizedRequest, user.userId());
        repository.log(user.userId(), user.companyId(), "VEHICLE_DEVICE_ASSIGNMENT", id, "VEHICLE_DEVICE_UPDATE");
    }

    public void removeVehicleDevice(JwtUserContext user, Long id) {
        assertAssignmentAccess(user, "vehicle_device_assignments", id);
        repository.removeVehicleDevice(id, user.userId());
        repository.log(user.userId(), user.companyId(), "VEHICLE_DEVICE_ASSIGNMENT", id, "VEHICLE_DEVICE_REMOVE");
    }

    public List<DriverManualAssignmentRow> driverManualList(JwtUserContext user) {
        return repository.driverManualList(user.companyId(), user.normalizedRole());
    }

    public Long createDriverManual(JwtUserContext user, DriverManualAssignmentRequest request) {
        validateDriverManual(user, request);
        Long id = repository.createDriverManual(request, user.userId());
        repository.log(user.userId(), user.companyId(), "DRIVER_MANUAL_ASSIGNMENT", id, "DRIVER_MANUAL_ASSIGN");
        return id;
    }

    public void updateDriverManual(JwtUserContext user, Long id, DriverManualAssignmentRequest request) {
        assertAssignmentAccess(user, "driver_manual_assignments", id);
        validateDriverManual(user, request);
        repository.updateDriverManual(id, request, user.userId());
        repository.log(user.userId(), user.companyId(), "DRIVER_MANUAL_ASSIGNMENT", id, "DRIVER_MANUAL_UPDATE");
    }

    public void removeDriverManual(JwtUserContext user, Long id) {
        assertAssignmentAccess(user, "driver_manual_assignments", id);
        repository.removeDriverManual(id, user.userId());
        repository.log(user.userId(), user.companyId(), "DRIVER_MANUAL_ASSIGNMENT", id, "DRIVER_MANUAL_REMOVE");
    }

    public List<AssignmentLookupOption> companyOptions(JwtUserContext user) {
        return repository.companyOptions(user.companyId(), user.normalizedRole());
    }

    public List<AssignmentLookupOption> vehicleOptions(JwtUserContext user) {
        return repository.vehicleOptions(user.companyId(), user.normalizedRole());
    }

    public List<AssignmentLookupOption> deviceOptions(JwtUserContext user) {
        return repository.deviceOptions(user.companyId(), user.normalizedRole());
    }

    public List<AssignmentLookupOption> driverOptions(JwtUserContext user) {
        return repository.driverOptions(user.companyId(), user.normalizedRole());
    }

    private Long resolveVehicleDeviceCompany(JwtUserContext user, Long requestedCompanyId, Long vehicleId, Long deviceId) {
        if (deviceId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device wajib dipilih.");
        }
        Long deviceCompanyId = repository.deviceCompanyId(deviceId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device tidak ditemukan atau sudah dihapus."));
        assertCompanyAccess(user, deviceCompanyId);
        if (vehicleId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vehicle wajib dipilih.");
        }
        Long vehicleCompanyId = repository.vehicleCompanyId(vehicleId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle tidak ditemukan atau sudah dihapus."));
        if (!vehicleCompanyId.equals(deviceCompanyId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vehicle harus berada dalam company yang sama dengan device.");
        }
        if (requestedCompanyId != null && !requestedCompanyId.equals(deviceCompanyId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company assignment harus mengikuti company device.");
        }
        return deviceCompanyId;
    }

    private void validateDriverManual(JwtUserContext user, DriverManualAssignmentRequest request) {
        Long effectiveCompanyId = resolveVehicleDeviceCompany(user, request.companyId(), request.vehicleId(), request.deviceId());
        if (request.driverId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver wajib diisi untuk manual driver pairing.");
        }
        validateDriverForCompany(user, request.driverId(), effectiveCompanyId);
    }

    private void validateDriverForCompany(JwtUserContext user, Long driverId, Long companyId) {
        Long driverCompanyId = repository.driverCompanyId(driverId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver tidak ditemukan."));
        assertCompanyAccess(user, driverCompanyId);
        if (!companyId.equals(driverCompanyId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver harus berada dalam company yang sama dengan device.");
        }
    }

    private void assertAssignmentAccess(JwtUserContext user, String table, Long id) {
        Long companyId = repository.assignmentCompanyId(table, id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment tidak ditemukan."));
        assertCompanyAccess(user, companyId);
    }

    private void assertCompanyAccess(JwtUserContext user, Long companyId) {
        if (!repository.canAccessCompany(companyId, user.companyId(), user.normalizedRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak memiliki akses assignment company tersebut.");
        }
    }
}
