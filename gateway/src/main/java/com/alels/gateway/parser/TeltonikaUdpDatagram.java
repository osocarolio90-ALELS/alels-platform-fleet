package com.alels.gateway.parser;

public record TeltonikaUdpDatagram(
        int channelPacketId,
        int avlPacketId,
        String imei,
        byte[] tcpCompatiblePacket,
        int codecId,
        int recordCount
) {}
