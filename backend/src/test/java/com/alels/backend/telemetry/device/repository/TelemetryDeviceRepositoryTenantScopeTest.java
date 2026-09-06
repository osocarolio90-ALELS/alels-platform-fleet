package com.alels.backend.telemetry.device.repository;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TelemetryDeviceRepositoryTenantScopeTest {
    private final TelemetryDeviceRepository repository = new TelemetryDeviceRepository(new JdbcTemplate());

    @Test
    void clientAndTechUsersAreRestrictedToTheirExactCompany() {
        assertEquals("AND d.company_id=?", repository.scope("CLIENT_USER", "d"));
        assertEquals("AND d.company_id=?", repository.scope("TECH-USER", "d"));
        assertArrayEquals(new Object[]{42L,42L}, repository.scopeParameters(42L,"CLIENT_USER"));
    }

    @Test
    void organizationRolesUseVisibleCompanyHierarchy() {
        assertEquals("AND d.company_id IN (SELECT id FROM visible_companies)", repository.scope("OWNER", "d"));
        assertArrayEquals(new Object[]{42L}, repository.scopeParameters(42L,"OWNER"));
    }

    @Test
    void administratorsDoNotReceiveAnAccidentalTenantPredicate() {
        assertEquals("",repository.scope("SUPER_ADMIN","d"));
        assertEquals("",repository.scope("ADMIN","d"));
        assertArrayEquals(new Object[]{42L},repository.scopeParameters(42L,"ADMIN"));
    }

    @Test
    void mapsVehicleStatusUsingTheRepositoryColumnAlias() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("vehicle_status")).thenReturn("TRIP");

        var row = repository.mapDeviceRow(resultSet, 0);

        assertEquals("TRIP", row.vehicleStatus());
    }
}
