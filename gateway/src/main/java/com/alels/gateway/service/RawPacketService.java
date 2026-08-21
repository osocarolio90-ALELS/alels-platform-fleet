package com.alels.gateway.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import com.alels.gateway.detector.ProtocolType;
import com.alels.gateway.repository.RawPacketRepository;
import com.alels.gateway.server.ChannelType;
import com.alels.gateway.util.HexUtil;

public class RawPacketService {

    public static Long logRawPacket(
            String imei,
            ProtocolType protocolType,
            ChannelType channelType,
            byte[] packet,
            String remoteAddress
    ) {

        System.out.println("========== RAW PACKET ==========");
        System.out.println("TIME     : " + LocalDateTime.now());
        System.out.println("IMEI     : " + safe(imei));
        System.out.println("PROTOCOL : " + protocolType);
        System.out.println("CHANNEL  : " + channelType);
        System.out.println("REMOTE   : " + remoteAddress);

        String rawJson = null;
        String rawHex = null;

        if (protocolType == ProtocolType.ALELS_JSON) {

            rawJson = new String(packet, StandardCharsets.UTF_8).trim();

            System.out.println("RAW JSON : " + rawJson);

        } else {

            rawHex = HexUtil.toHex(packet);

            System.out.println("RAW HEX  : " + rawHex);
        }

        System.out.println("================================");

        return RawPacketRepository.insert(
                imei,
                protocolType.name(),
                channelType.name(),
                remoteAddress,
                "IN",
                rawJson,
                rawHex,
                packet.length
        );
    }

    private static String safe(String value) {
        return value == null || value.isBlank()
                ? "-"
                : value;
    }
}
