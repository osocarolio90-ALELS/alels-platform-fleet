package com.alels.gateway.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.alels.gateway.model.TelemetryData;
import com.alels.gateway.normalizer.AlelsJsonTelemetryNormalizer;
import com.alels.gateway.parser.ParserResult;
import com.alels.gateway.parser.TeltonikaCodec8EParser;
import com.alels.gateway.parser.TeltonikaCodec8Parser;
import com.alels.gateway.util.TeltonikaCrc16;

/** C2-R1C compatibility evidence; this test does not implement or activate identity generation. */
class CanonicalIdentityV1CompatibilityTest {
    private static final long TIME = 1_700_000_000_123L;

    @Test
    void ci01ReusedAlelsSequenceAndTimestampDoNotExposeAResetEpoch() throws Exception {
        byte[] queuedEvent = json("68", "");
        TelemetryData beforeReset = AlelsJsonTelemetryNormalizer.normalize(queuedEvent);
        TelemetryData afterReset = AlelsJsonTelemetryNormalizer.normalize(queuedEvent.clone());
        assertEquals(beforeReset.getPacketSequence(), afterReset.getPacketSequence());
        assertEquals(beforeReset.getDeviceTime(), afterReset.getDeviceTime());
        assertEquals(beforeReset.getIoData(), afterReset.getIoData());
        // These identical source bytes can describe a resend or an independent event after reset.
        assertEquals("2023-11-14 22:13:20", beforeReset.getDeviceTime());
    }

    @Test
    void ci02MissingAlelsSequenceIsIndistinguishableFromZeroAfterNormalization() throws Exception {
        TelemetryData absent = AlelsJsonTelemetryNormalizer.normalize(
                "{\"T\":\"data\",\"A\":\"863719066421737\"}".getBytes(StandardCharsets.UTF_8));
        TelemetryData zero = AlelsJsonTelemetryNormalizer.normalize(json("0", ""));
        assertEquals(0, absent.getPacketSequence());
        assertEquals(absent.getPacketSequence(), zero.getPacketSequence());
        assertEquals("", absent.getDeviceTime());
    }

    @Test
    void ci03OptionalFutureEventIdCurrentlyEntersIoAndNeedsExplicitReservation() throws Exception {
        TelemetryData current = AlelsJsonTelemetryNormalizer.normalize(json("68", ""));
        TelemetryData extended = AlelsJsonTelemetryNormalizer.normalize(
                json("68", ",\"sourceEventId\":\"018e31e0-0000-4000-8000-000000000001\""));
        assertEquals(current.getPacketSequence(), extended.getPacketSequence());
        assertEquals(current.getDeviceTime(), extended.getDeviceTime());
        assertEquals(current.getLatitude(), extended.getLatitude());
        assertEquals(current.getIoData().get("1"), extended.getIoData().get("1"));
        assertEquals("018e31e0-0000-4000-8000-000000000001", extended.getIoData().get("sourceEventId"));
    }

    @Test
    void ci04BothCodecsPreserveMillisecondsAndDistinguishRepeatedTimesWithinOnePacket() throws Exception {
        for (int codec : new int[] {0x08, 0x8e}) {
            List<TelemetryData> records = parse(codec, TIME, TIME).getTelemetryList();
            assertEquals(2, records.size());
            assertEquals("2023-11-14T22:13:20.123Z", records.getFirst().getDeviceTime());
            assertEquals(records.getFirst().getDeviceTime(), records.getLast().getDeviceTime());
            assertNotEquals(records.getFirst().getPacketSequence(), records.getLast().getPacketSequence());
            assertEquals(1, records.getFirst().getPriority());
            assertEquals(1, records.getFirst().getEventIoId());
            assertEquals(7, ((Number) records.getFirst().getIoData().get("1")).intValue());
        }
    }

    @Test
    void ci05BothCodecsRepeatDerivedSequenceAcrossIndependentIdenticalPackets() throws Exception {
        for (int codec : new int[] {0x08, 0x8e}) {
            TelemetryData first = parse(codec, TIME).getTelemetryList().getFirst();
            TelemetryData second = parse(codec, TIME).getTelemetryList().getFirst();
            assertEquals(first.getPacketSequence(), second.getPacketSequence());
            assertEquals(first.getIoData(), second.getIoData());
        }
    }

    @Test
    void ci06RepackingOneCodecRecordChangesTheDerivedSequence() throws Exception {
        for (int codec : new int[] {0x08, 0x8e}) {
            TelemetryData inBatch = parse(codec, TIME - 1000, TIME).getTelemetryList().getLast();
            TelemetryData alone = parse(codec, TIME).getTelemetryList().getFirst();
            assertEquals(inBatch.getDeviceTime(), alone.getDeviceTime());
            assertEquals(inBatch.getIoData(), alone.getIoData());
            assertNotEquals(inBatch.getPacketSequence(), alone.getPacketSequence());
        }
    }

    @Test
    void ci07LateCodecRecordRetainsItsSourceTimestamp() throws Exception {
        for (int codec : new int[] {0x08, 0x8e}) {
            List<TelemetryData> records = parse(codec, TIME + 1000, TIME).getTelemetryList();
            assertTrue(records.getFirst().getPacketSequence() > records.getLast().getPacketSequence());
            assertEquals("2023-11-14T22:13:20.123Z", records.getLast().getDeviceTime());
        }
    }

    private byte[] json(String sequence, String additionalFields) {
        return ("{\"T\":\"data\",\"Q\":" + sequence
                + ",\"A\":\"863719066421737\",\"B\":\"2023-11-14 22:13:20\","
                + "\"D\":1070000000,\"E\":-62000000,\"S\":123,\"1\":7" + additionalFields + "}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private ParserResult parse(int codec, long... timestamps) throws Exception {
        ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
        DataOutputStream body = new DataOutputStream(bodyBytes);
        body.writeByte(codec);
        body.writeByte(timestamps.length);
        for (long timestamp : timestamps) {
            body.writeLong(timestamp);
            body.writeByte(1);
            body.writeInt(1_070_000_000);
            body.writeInt(-62_000_000);
            body.writeShort(26);
            body.writeShort(90);
            body.writeByte(12);
            body.writeShort(10);
            width(body, codec, 1); // Event IO ID.
            width(body, codec, 1); // Total IO count.
            width(body, codec, 1); // One one-byte IO.
            width(body, codec, 1);
            body.writeByte(7);
            for (int i = 0; i < 3; i++) width(body, codec, 0);
            if (codec == 0x8e) body.writeShort(0); // NX.
        }
        body.writeByte(timestamps.length);
        byte[] data = bodyBytes.toByteArray();
        ByteArrayOutputStream frameBytes = new ByteArrayOutputStream();
        DataOutputStream frame = new DataOutputStream(frameBytes);
        frame.writeInt(0);
        frame.writeInt(data.length);
        frame.write(data);
        frame.writeInt(TeltonikaCrc16.calculate(data, 0, data.length));
        ParserResult result = codec == 0x08
                ? new TeltonikaCodec8Parser().parse(frameBytes.toByteArray())
                : new TeltonikaCodec8EParser().parse(frameBytes.toByteArray());
        assertTrue(result.isValid());
        return result;
    }

    private void width(DataOutputStream output, int codec, int value) throws Exception {
        if (codec == 0x08) output.writeByte(value);
        else output.writeShort(value);
    }
}
