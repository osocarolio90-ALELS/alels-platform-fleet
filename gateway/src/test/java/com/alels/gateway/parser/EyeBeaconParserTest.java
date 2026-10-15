package com.alels.gateway.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.alels.gateway.normalizer.AlelsJsonTelemetryNormalizer;
import com.alels.gateway.normalizer.EyeBeaconTelemetryNormalizer;

class EyeBeaconParserTest {
    @Test
    void parsesDocumentedBaseFirmwareExamples() {
        var eddystone = EyeBeaconParser.parse(385, "1101E39B606AA38255AA8E460B154E2D0055CF");
        assertEquals("EDDYSTONE", eddystone.beacons().get(0).type());
        assertEquals(-49, eddystone.beacons().get(0).rssiDbm());
        assertEquals("E39B606AA38255AA8E460B154E2D0055", eddystone.beacons().get(0).id());

        var ib = EyeBeaconParser.parse(385,
                "11216B817F8A274D4FBDB62D33E1842F8DF8014D022BBF21A579723675064DC396A7C3520129F61900000000BF");
        assertEquals(2, ib.beacons().size());
        assertEquals("IBEACON", ib.beacons().get(0).type());
        assertEquals(-65, ib.beacons().get(1).rssiDbm());
    }

    @Test
    void parsesDocumentedAdvancedFirmwareExample() {
        String value = "011c0001a40110eb47706aa38255aa96f21a154e2d00550d01000e020bd6";
        for (int ioId : new int[] {10828, 10829, 10831}) {
            var result = EyeBeaconParser.parse(ioId, value);
            assertEquals(1, result.beacons().size());
            var beacon = result.beacons().get(0);
            assertEquals(-92, beacon.rssiDbm());
            assertEquals(false, beacon.lowBattery());
            assertEquals(3030, beacon.batteryMillivolts());
        }
    }

    @Test
    void parsesAdvanced548WithCustomBeaconIdAndAdditionalData() {
        var result = EyeBeaconParser.parse(548, "010B0001DC01067CD9F418D9C0");
        assertEquals(1, result.beacons().size());
        assertEquals("CUSTOM", result.beacons().get(0).type());
        assertEquals("7CD9F418D9C0", result.beacons().get(0).id());
        assertEquals(-36, result.beacons().get(0).rssiDbm());

        var detailed = EyeBeaconParser.parse(548,
                "01360001B10110F34B6F6AA38255AA9EF619154E2D0055021F"
                + "0201060303AAFE1716AAFE0002F34B6F6AA38255AA9EF619154E2D00550000");
        assertEquals(-79, detailed.beacons().get(0).rssiDbm());
        assertEquals(62, detailed.beacons().get(0).otherParameters().get(2).length());
    }

    @Test
    void parsesOlderAdvanced548Layout() {
        var result = EyeBeaconParser.parse(548, "01B714ACFD065EC3C011E39BBE1A514932AC44000546091E0201061AFF4C000215ACFD065EC3C011E39BBE1A514932AC4400054609CE");
        assertEquals(1, result.beacons().size());
        assertEquals(-73, result.beacons().get(0).rssiDbm());
        assertEquals(60, result.beacons().get(0).otherParameters().get(2).length());
    }

    @Test
    void keepsOneHundredAdvancedBeaconRecordsInOrder() {
        StringBuilder hex = new StringBuilder("01");
        for (int index = 0; index < 100; index++) {
            hex.append("150001B00110");
            hex.append("00".repeat(15));
            hex.append(String.format("%02X", index));
        }
        var result = EyeBeaconParser.parse(548, hex.toString());
        assertEquals(100, result.beacons().size());
        assertEquals("00000000000000000000000000000063", result.beacons().get(99).id());
    }

    @Test
    void preservesUndocumentedWifiVariantWithoutInventingFields() {
        String sample = "112500000000000000000000000000000000F410C8E3D6B70E";
        var result = EyeBeaconParser.parse(10500, sample);
        assertTrue(result.beacons().isEmpty());
        assertEquals(sample.substring(2), result.undecodedHex());
        assertThrows(IllegalArgumentException.class, () -> EyeBeaconParser.parse(10500, "1121FF"));
        var mixed = EyeBeaconParser.parse(10500,
                "112100000000000000000000000000000000F4134EE1AB"
                + "2500000000000000000000000000000000F410C8E3D6B70E");
        assertEquals(1, mixed.beacons().size());
        assertEquals(-85, mixed.beacons().get(0).rssiDbm());
        assertTrue(mixed.undecodedHex().startsWith("25"));
    }

    @Test
    void wifiBeaconValuesRemainAttachedToTheirOwnImei() throws Exception {
        String firstHex = "1101E39B606AA38255AA8E460B154E2D0055CF";
        String secondHex = "112500000000000000000000000000000000F410C8E3D6B70E";
        var first = AlelsJsonTelemetryNormalizer.normalize(json("863719066421737", firstHex));
        var second = AlelsJsonTelemetryNormalizer.normalize(json("352093086403655", secondHex));

        assertEquals("863719066421737", first.getImei());
        assertEquals("352093086403655", second.getImei());
        assertEquals(firstHex, first.getIoData().get("10500"));
        assertEquals(secondHex, second.getIoData().get("10500"));
        assertEquals(1, EyeBeaconParser.parse(10500, (String) first.getIoData().get("10500")).beacons().size());
        assertTrue(EyeBeaconParser.parse(10500, (String) second.getIoData().get("10500")).beacons().isEmpty());
        assertEquals(firstHex, first.getIoData().get("10500"));
    }

    @Test
    void normalizesOnlyBeaconIoOfEachTelemetryRecord() throws Exception {
        var first = AlelsJsonTelemetryNormalizer.normalize(json("863719066421737",
                "1101E39B606AA38255AA8E460B154E2D0055CF"));
        var second = AlelsJsonTelemetryNormalizer.normalize(json("352093086403655",
                "112500000000000000000000000000000000F410C8E3D6B70E"));
        EyeBeaconTelemetryNormalizer.normalize(first);
        EyeBeaconTelemetryNormalizer.normalize(second);
        assertEquals(1, first.getEyeBeacons().get(0).beacons().size());
        assertEquals(0, second.getEyeBeacons().get(0).beacons().size());
        assertFalse(second.getEyeBeacons().get(0).undecodedHex().isEmpty());
        assertEquals("863719066421737", first.getImei());
        assertEquals("352093086403655", second.getImei());
    }

    @Test
    void normalizesAdvanced548FromAlelsJson() throws Exception {
        String hex = "010B0001DC01067CD9F418D9C0";
        byte[] json = ("{\"T\":\"data\",\"A\":\"863719066421737\",\"K\":548,\"548\":\""
                + hex + "\"}").getBytes(StandardCharsets.UTF_8);
        var telemetry = AlelsJsonTelemetryNormalizer.normalize(json);
        EyeBeaconTelemetryNormalizer.normalize(telemetry);

        assertEquals(hex, telemetry.getIoData().get("548"));
        assertEquals("7CD9F418D9C0", telemetry.getEyeBeacons().get(0).beacons().get(0).id());
    }

    private static byte[] json(String imei, String hex) {
        return ("{\"T\":\"data\",\"A\":\"" + imei + "\",\"K\":10500,\"10500\":\""
                + hex + "\"}").getBytes(StandardCharsets.UTF_8);
    }
}
