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

    public Overview overview(JwtUserContext user,Long afterId,int requestedLimit,String search,String folder) {
        int limit=Math.max(10,Math.min(requestedLimit,100));
        long cursor=afterId==null?0:Math.max(afterId,0);
        List<com.alels.backend.telemetry.device.dto.TelemetryDeviceDtos.DeviceRow> page=
            repository.list(user.companyId(),user.normalizedRole(),cursor,limit+1,search,folder);
        boolean hasMore=page.size()>limit;
        List<com.alels.backend.telemetry.device.dto.TelemetryDeviceDtos.DeviceRow> devices=
            hasMore?List.copyOf(page.subList(0,limit)):page;
        return new Overview(
            repository.folders(user.companyId(),user.normalizedRole(),false),
            repository.folders(user.companyId(),user.normalizedRole(),true),
            devices,
            repository.count(user.companyId(),user.normalizedRole(),"","ALL"),
            repository.count(user.companyId(),user.normalizedRole(),search,folder),
            repository.count(user.companyId(),user.normalizedRole(),"","UNGROUP"),
            hasMore?devices.get(devices.size()-1).id():null,
            hasMore
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
