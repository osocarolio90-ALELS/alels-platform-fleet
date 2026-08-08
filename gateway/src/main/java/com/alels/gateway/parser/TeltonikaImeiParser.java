package com.alels.gateway.parser;

import com.alels.gateway.detector.ProtocolType;
import java.nio.charset.StandardCharsets;

public class TeltonikaImeiParser implements PacketParser {
    @Override
    public ParserResult parse(byte[] packet) {
        if (packet == null || packet.length < 3) {
            return new ParserResult(null, ProtocolType.TELTONIKA_IMEI, 0, false);
        }
        int len = ((packet[0] & 0xFF) << 8) | (packet[1] & 0xFF);
        if (len != 15 || packet.length != len + 2) {
            return new ParserResult(null, ProtocolType.TELTONIKA_IMEI, 0, false);
        }
        String imei = new String(packet, 2, len, StandardCharsets.US_ASCII);
        return new ParserResult(imei, ProtocolType.TELTONIKA_IMEI, 0, imei.matches("[0-9]{15}"));
    }
}
