package com.alels.gateway.ack;

import java.nio.ByteBuffer;

public class TeltonikaAckService implements AckService {
    @Override
    public byte[] buildAck(int acceptedRecords) {
        return ByteBuffer.allocate(4).putInt(acceptedRecords).array();
    }

    public byte[] buildImeiAcceptedAck() {
        return new byte[] { 0x01 };
    }

    public byte[] buildImeiRejectedAck() {
        return new byte[] { 0x00 };
    }
}
