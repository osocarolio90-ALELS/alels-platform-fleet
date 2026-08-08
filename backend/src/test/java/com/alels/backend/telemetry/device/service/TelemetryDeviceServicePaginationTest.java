package com.alels.backend.telemetry.device.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.telemetry.device.dto.TelemetryDeviceDtos.DeviceRow;
import com.alels.backend.telemetry.device.dto.TelemetryDeviceDtos.Overview;
import com.alels.backend.telemetry.device.repository.TelemetryDeviceRepository;

class TelemetryDeviceServicePaginationTest {
    @Test
    void capsRequestAtOneHundredAndReturnsStableKeysetCursor() {
        TelemetryDeviceRepository repository=mock(TelemetryDeviceRepository.class);
        List<DeviceRow> page=new ArrayList<>();
        for(long id=1;id<=101;id++) page.add(row(id));
        when(repository.list(20L,"CLIENTUSER",0,101,"","ALL")).thenReturn(page);
        when(repository.folders(20L,"CLIENTUSER",false)).thenReturn(List.of());
        when(repository.folders(20L,"CLIENTUSER",true)).thenReturn(List.of());
        when(repository.count(20L,"CLIENTUSER","","ALL")).thenReturn(101L);
        when(repository.count(20L,"CLIENTUSER","","UNGROUP")).thenReturn(101L);

        Overview result=new TelemetryDeviceService(repository).overview(
                new JwtUserContext(10L,20L,"CLIENTUSER","user@example.test",1L),0L,10_000,"","ALL");

        assertEquals(100,result.devices().size());
        assertEquals(100L,result.nextCursor());
        assertTrue(result.hasMore());
        verify(repository).list(20L,"CLIENTUSER",0,101,"","ALL");
    }

    private DeviceRow row(long id) {
        return new DeviceRow(id,"123456789012345","-","-","-","-","-","-",BigDecimal.ZERO,
                "-","-","-","STOP",true,false,null,null,false,"Company",null);
    }
}
