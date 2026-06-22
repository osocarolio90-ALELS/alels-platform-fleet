package com.alels.gateway.parser;

import com.alels.gateway.detector.ProtocolType;
import java.nio.charset.StandardCharsets;

public class TeltonikaImeiParser implements PacketParser {
    @Override
    public ParserResult parse(byte[] packet) {
        int len = ((packet[0] & 0xFF) << 8) | (packet[1] & 0xFF);
        String imei = new String(packet, 2, len, StandardCharsets.US_ASCII);
        return new ParserResult(imei, ProtocolType.TELTONIKA_IMEI, 0, true);
    }
}
