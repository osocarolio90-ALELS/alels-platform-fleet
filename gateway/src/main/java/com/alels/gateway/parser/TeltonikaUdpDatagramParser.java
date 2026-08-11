package com.alels.gateway.parser;

import com.alels.gateway.util.TeltonikaCrc16;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/** Decodes the Teltonika UDP channel envelope and reuses the canonical TCP AVL parsers. */
public final class TeltonikaUdpDatagramParser {
    private static final int IMEI_LENGTH = 15;

    public Optional<TeltonikaUdpDatagram> parse(byte[] datagram) {
        if (datagram == null || datagram.length < 24) return Optional.empty();

        ByteBuffer reader = ByteBuffer.wrap(datagram);
        int declaredLength = Short.toUnsignedInt(reader.getShort());
        if (declaredLength != datagram.length - 2) return Optional.empty();

        int channelPacketId = Short.toUnsignedInt(reader.getShort());
        if (Byte.toUnsignedInt(reader.get()) != 0x01) return Optional.empty();

        int avlPacketId = Byte.toUnsignedInt(reader.get());
        int imeiLength = Short.toUnsignedInt(reader.getShort());
        if (imeiLength != IMEI_LENGTH || reader.remaining() <= imeiLength + 1) return Optional.empty();

        byte[] imeiBytes = new byte[imeiLength];
        reader.get(imeiBytes);
        String imei = new String(imeiBytes, StandardCharsets.US_ASCII);
        if (!imei.matches("\\d{15}")) return Optional.empty();

        byte[] avlData = Arrays.copyOfRange(datagram, reader.position(), datagram.length);
        int codecId = Byte.toUnsignedInt(avlData[0]);
        if (codecId != 0x08 && codecId != 0x8E) return Optional.empty();
        int recordCount = Byte.toUnsignedInt(avlData[1]);

        byte[] tcpPacket = new byte[avlData.length + 12];
        ByteBuffer tcp = ByteBuffer.wrap(tcpPacket);
        tcp.putInt(0);
        tcp.putInt(avlData.length);
        tcp.put(avlData);
        tcp.putInt(TeltonikaCrc16.calculate(tcpPacket, 8, avlData.length));

        return Optional.of(new TeltonikaUdpDatagram(
                channelPacketId, avlPacketId, imei, tcpPacket, codecId, recordCount
        ));
    }

    public byte[] acknowledgement(TeltonikaUdpDatagram datagram, int acceptedRecords) {
        return ByteBuffer.allocate(7)
                .putShort((short) 5)
                .putShort((short) datagram.channelPacketId())
                .put((byte) 0x01)
                .put((byte) datagram.avlPacketId())
                .put((byte) acceptedRecords)
                .array();
    }
}
