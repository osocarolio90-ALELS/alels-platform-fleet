package com.alels.gateway.parser;

public interface PacketParser {
    ParserResult parse(byte[] packet) throws Exception;
}
