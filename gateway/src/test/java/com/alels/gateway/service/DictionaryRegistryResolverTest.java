package com.alels.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.alels.gateway.model.DeviceModelInfo;

class DictionaryRegistryResolverTest {
    @Test
    void alelsHubAlwaysUsesTheFmc650DictionaryContract() {
        DeviceModelInfo hub = new DeviceModelInfo();
        hub.setModelId(99L);
        hub.setBrandCode("ALELS");
        hub.setModelCode("ALELS_HUB");
        hub.setParserCode("ALELS_JSON");
        hub.setDictionaryCode("ALELS_HUBIO");

        assertTrue(DictionaryRegistryResolver.usesAlelsHubFmc650Contract(hub));
        assertEquals("fmc650", DictionaryRegistryResolver.resolveDictionaryCode(hub));
    }
}
