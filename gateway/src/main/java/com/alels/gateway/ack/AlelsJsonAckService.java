package com.alels.gateway.ack;

import java.nio.charset.StandardCharsets;

public class AlelsJsonAckService implements AckService {
    @Override
    public byte[] buildAck(int acceptedRecords) {
        String json = "{\"T\":\"ack\",\"accepted\":" + acceptedRecords + "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
