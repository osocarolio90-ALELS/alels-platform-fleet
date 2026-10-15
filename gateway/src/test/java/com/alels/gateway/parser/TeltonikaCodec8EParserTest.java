package com.alels.gateway.parser;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import org.junit.jupiter.api.Test;

import com.alels.gateway.normalizer.EyeBeaconTelemetryNormalizer;
import com.alels.gateway.util.TeltonikaCrc16;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeltonikaCodec8EParserTest {

    private static final String VALID_FRAME =
            "000000000000004A8E010000016B412CEE000100000000000000000000000000000000010005000100010100010011001D00010010015E2C880002000B000000003544C87A000E000000001DD7E06A00000100002994";

    @Test
    void acceptsGoldenCodec8EFrameIncludingVariableIoCount() {
        ParserResult result = new TeltonikaCodec8EParser().parse(hex(VALID_FRAME));

        assertTrue(result.isValid());
        assertEquals(1, result.getRecordCount());
        assertEquals(1, result.getTelemetryList().size());
    }

    @Test
    void rejectsCorruptedCrc() {
        byte[] corrupted = hex(VALID_FRAME);
        corrupted[corrupted.length - 1] ^= 0x01;

        assertFalse(new TeltonikaCodec8EParser().parse(corrupted).isValid());
    }

    @Test
    void extractsAndNormalizesBothWifiBeaconIoIdsFromCodec8E() throws Exception {
        String advanced = "010B0001DC01067CD9F418D9C0";
        String simple = "112500000000000000000000000000000000F410C8E3D6B70E";
        ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
        DataOutputStream body = new DataOutputStream(bodyBytes);
        body.writeByte(0x8E);
        body.writeByte(1);
        body.writeLong(1_700_000_000_000L);
        body.writeByte(0);
        body.writeInt(0);
        body.writeInt(0);
        body.writeShort(0);
        body.writeShort(0);
        body.writeByte(0);
        body.writeShort(0);
        body.writeShort(548);
        body.writeShort(2);
        for (int i = 0; i < 4; i++) body.writeShort(0);
        body.writeShort(2);
        for (String entry : new String[] {"548:" + advanced, "10500:" + simple}) {
            String[] parts = entry.split(":", 2);
            byte[] value = hex(parts[1]);
            body.writeShort(Integer.parseInt(parts[0]));
            body.writeShort(value.length);
            body.write(value);
        }
        body.writeByte(1);
        byte[] data = bodyBytes.toByteArray();
        ByteArrayOutputStream frameBytes = new ByteArrayOutputStream();
        DataOutputStream frame = new DataOutputStream(frameBytes);
        frame.writeInt(0);
        frame.writeInt(data.length);
        frame.write(data);
        frame.writeInt(TeltonikaCrc16.calculate(data, 0, data.length));

        ParserResult parsed = new TeltonikaCodec8EParser().parse(frameBytes.toByteArray());
        assertTrue(parsed.isValid());
        var telemetry = parsed.getTelemetryList().get(0);
        assertEquals(advanced, telemetry.getIoData().get("548"));
        assertEquals(simple, telemetry.getIoData().get("10500"));
        EyeBeaconTelemetryNormalizer.normalize(telemetry);
        assertEquals(2, telemetry.getEyeBeacons().size());
        assertEquals("7CD9F418D9C0", telemetry.getEyeBeacons().get(0).beacons().get(0).id());
        assertEquals(simple.substring(2), telemetry.getEyeBeacons().get(1).undecodedHex());
    }

    private byte[] hex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
