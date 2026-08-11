package com.alels.backend.assetregister.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.assetregister.dto.DeviceRegisterDtos.DeviceLookupOption;
import com.alels.backend.assetregister.dto.DeviceRegisterDtos.DeviceRegisterRequest;
import com.alels.backend.assetregister.dto.DeviceRegisterDtos.DeviceRegisterRow;
import com.alels.backend.assetregister.repository.DeviceRegisterRepository;
import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.serverops.shared.config.GatewayPublicEndpoint;
import com.alels.backend.serverops.shared.config.GatewayPublicEndpoint.Endpoint;

@Service
public class DeviceRegisterService {
    private final DeviceRegisterRepository repository;
    private final GatewayPublicEndpoint gatewayEndpoint;

    public DeviceRegisterService(DeviceRegisterRepository repository, GatewayPublicEndpoint gatewayEndpoint) {
        this.repository = repository;
        this.gatewayEndpoint = gatewayEndpoint;
    }

    public List<DeviceRegisterRow> list(JwtUserContext user) { return repository.list(user.companyId(), user.normalizedRole()); }
    public List<DeviceLookupOption> companyOptions(JwtUserContext user) { return repository.companyOptions(user.companyId(), user.normalizedRole()); }
    public List<DeviceLookupOption> brandOptions() { return repository.brandOptions(); }
    public List<DeviceLookupOption> modelOptions(Long brandId) { return repository.modelOptions(brandId); }

    public Long create(JwtUserContext user, DeviceRegisterRequest request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request device wajib diisi.");
        Long targetCompanyId = request.companyId() == null ? user.companyId() : request.companyId();
        assertCompanyAccess(user, targetCompanyId);
        DeviceRegisterRequest normalized = withGatewayEndpoint(request, targetCompanyId);
        validate(normalized, null);
        Long id = repository.create(normalized, user.userId());
        repository.log(user.userId(), user.companyId(), id, "DEVICE_CREATE");
        return id;
    }

    public void update(JwtUserContext user, Long id, DeviceRegisterRequest request) {
        Long currentCompanyId = repository.companyIdByDevice(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found."));
        assertCompanyAccess(user, currentCompanyId);
        DeviceRegisterRequest normalized = withGatewayEndpoint(request, currentCompanyId);
        validate(normalized, id);
        repository.update(id, normalized, user.userId());
        repository.log(user.userId(), user.companyId(), id, "DEVICE_UPDATE");
    }

    public void delete(JwtUserContext user, Long id) {
        assertDeviceAccess(user, id);
        repository.softDelete(id, user.userId());
        repository.log(user.userId(), user.companyId(), id, "DEVICE_DELETE");
    }

    private void validate(DeviceRegisterRequest request, Long excludeId) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request device wajib diisi.");
        if (request.imei() == null || request.imei().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device IMEI wajib diisi.");
        if (request.imei().trim().length() < 8 || request.imei().trim().length() > 32) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device IMEI tidak valid.");
        if (repository.imeiExists(request.imei(), excludeId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Device IMEI sudah terdaftar.");
        if (!repository.modelExists(request.deviceModelId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device tidak valid.");
        if (request.deviceBrandId() != null && !repository.brandExists(request.deviceBrandId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device Brand tidak valid.");
        if (request.deviceBrandId() != null && !repository.modelBelongsToBrand(request.deviceModelId(), request.deviceBrandId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device tidak sesuai Device Brand.");
        if (request.tcpPort() != null && (request.tcpPort() < 1 || request.tcpPort() > 65535)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TCP port tidak valid.");
    }

    private void assertDeviceAccess(JwtUserContext user, Long id) {
        Long companyId = repository.companyIdByDevice(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found."));
        assertCompanyAccess(user, companyId);
    }

    private DeviceRegisterRequest withGatewayEndpoint(DeviceRegisterRequest request, Long companyId) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request device wajib diisi.");
        Endpoint endpoint = gatewayEndpoint.configured().orElseThrow(() -> new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Public gateway host/port belum dikonfigurasi."
        ));
        return new DeviceRegisterRequest(
                companyId, request.deviceBrandId(), request.deviceModelId(), request.imei(),
                request.gsmNumber(), endpoint.host(), endpoint.port(), request.registerStatus(), request.notes()
        );
    }
    private void assertCompanyAccess(JwtUserContext user, Long companyId) {
        if (!repository.canAccessCompany(companyId, user.companyId(), user.normalizedRole())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak memiliki akses ke company device tersebut.");
    }
}
