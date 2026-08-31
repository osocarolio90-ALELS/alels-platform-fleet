package com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeviceParameterGroupCatalogTest {
    @Test
    void fmbAndFmcUseTheSameCatalogWithinADeviceSeries() {
        assertEquals(DeviceParameterGroupCatalog.groups("FMB003", "FMB003"), DeviceParameterGroupCatalog.groups("FMC003", "FMC003"));
        assertEquals(DeviceParameterGroupCatalog.groups("FMB150", "FMB150"), DeviceParameterGroupCatalog.groups("FMC150", "FMC150"));
        assertEquals(7, DeviceParameterGroupCatalog.groups("FMC003", null).size());
        assertEquals(7, DeviceParameterGroupCatalog.groups("FMC150", null).size());
        assertEquals(18, DeviceParameterGroupCatalog.groups("FMC650", null).size());
    }

    @Test
    void alelsHubUsesTheFmc650AvlContract() {
        assertEquals(DeviceParameterGroupCatalog.groups("FMC650", "FMC650"), DeviceParameterGroupCatalog.groups("ALELS HUB", "FMC650"));
        assertEquals("CAN adapters elements", DeviceParameterGroupCatalog.canonicalGroup("ALELS HUB", "FMC650", "LVCAN elements"));
    }

    @Test
    void canonicalizesKnownDictionaryCategoryVariantsWithoutInventingValues() {
        assertEquals("OBD OEM elements", DeviceParameterGroupCatalog.canonicalGroup("FMC003", "FMC003", "LVCAN, ALLCAN300"));
        assertEquals("CAN I/O standard elements", DeviceParameterGroupCatalog.canonicalGroup("FMC150", "FMC150", "CAN Chip"));
        assertEquals("BLE Sensors I/O elements", DeviceParameterGroupCatalog.canonicalGroup("FMC150", "FMC150", "Bluetooth®Low Energy"));
        assertTrue(DeviceParameterGroupCatalog.groups("FMC650", "FMC650").contains("Apache Thermograph"));
    }
}
