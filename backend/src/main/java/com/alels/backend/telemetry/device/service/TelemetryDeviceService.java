package com.alels.backend.telemetry.device.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.telemetry.device.dto.TelemetryDeviceDtos.Overview;
import com.alels.backend.telemetry.device.repository.TelemetryDeviceRepository;

@Service
public class TelemetryDeviceService {
    private final TelemetryDeviceRepository repository;
    public TelemetryDeviceService(TelemetryDeviceRepository repository) { this.repository=repository; }

    public Overview overview(JwtUserContext user) {
        return new Overview(
            repository.folders(user.companyId(),user.normalizedRole(),false),
            repository.folders(user.companyId(),user.normalizedRole(),true),
            repository.list(user.companyId(),user.normalizedRole())
        );
    }

    @Transactional
    public void setTcp(JwtUserContext user,Long id,boolean enabled) {
        if ("TECHUSER".equals(user.normalizedRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"TECH USER memiliki akses view only.");
        }
        String imei=repository.imeiById(id,user.companyId(),user.normalizedRole())
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Device tidak ditemukan atau di luar scope."));
        repository.setTcp(id,enabled);
        if (repository.imeiById(id,user.companyId(),user.normalizedRole()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"TCP device "+imei+" gagal diperbarui.");
        }
    }
}
