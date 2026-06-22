package com.alels.gateway.ack;

public interface AckService {
    byte[] buildAck(int acceptedRecords);
}
