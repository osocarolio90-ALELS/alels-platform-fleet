package com.alels.gateway.netty;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

class NettyProtocolDecoderTest {

    private static final String CODEC8E_FRAME =
            "000000000000004A8E010000016B412CEE000100000000000000000000000000000000010005000100010100010011001D00010010015E2C880002000B000000003544C87A000E000000001DD7E06A00000100002994";

    @Test
    void waitsForFragmentedJsonAndEmitsExactlyOneFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(new NettyProtocolDecoder());

        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer("{\"T\":\"da".getBytes(StandardCharsets.UTF_8))));
        assertTrue(channel.writeInbound(Unpooled.wrappedBuffer("ta\",\"A\":\"123\"}".getBytes(StandardCharsets.UTF_8))));

        assertArrayEquals(
                "{\"T\":\"data\",\"A\":\"123\"}".getBytes(StandardCharsets.UTF_8),
                channel.readInbound()
        );
        assertFalse(channel.finish());
    }

    @Test
    void emitsCoalescedJsonFramesSeparately() {
        EmbeddedChannel channel = new EmbeddedChannel(new NettyProtocolDecoder());
        assertTrue(channel.writeInbound(Unpooled.wrappedBuffer("{\"T\":\"hb\"}\n{\"T\":\"hb\"}\r\n".getBytes(StandardCharsets.UTF_8))));

        assertArrayEquals("{\"T\":\"hb\"}".getBytes(StandardCharsets.UTF_8), channel.readInbound());
        assertArrayEquals("{\"T\":\"hb\"}".getBytes(StandardCharsets.UTF_8), channel.readInbound());
        assertFalse(channel.finish());
    }

    @Test
    void waitsForCompleteImeiHandshake() {
        EmbeddedChannel channel = new EmbeddedChannel(new NettyProtocolDecoder());
        byte[] first = new byte[] {0, 15, '1', '2'};
        byte[] second = "3456789012345".getBytes(StandardCharsets.US_ASCII);

        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(first)));
        assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(second)));

        byte[] expected = new byte[17];
        System.arraycopy(first, 0, expected, 0, first.length);
        System.arraycopy(second, 0, expected, first.length, second.length);
        assertArrayEquals(expected, channel.readInbound());
        assertFalse(channel.finish());
    }

    @Test
    void reconstructsCodec8EFrameAtEveryPossibleTcpSplit() {
        byte[] frame=hex(CODEC8E_FRAME);
        for(int split=1;split<frame.length;split++) {
            EmbeddedChannel channel=new EmbeddedChannel(new NettyProtocolDecoder());
            assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(frame,0,split)),"split="+split);
            assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(frame,split,frame.length-split)),"split="+split);
            assertArrayEquals(frame,channel.readInbound(),"split="+split);
            assertFalse(channel.finish());
        }
    }

    @Test
    void emitsCoalescedImeiAndCodecFrameSeparately() {
        byte[] handshake=new byte[17];
        handshake[1]=15;
        System.arraycopy("123456789012345".getBytes(StandardCharsets.US_ASCII),0,handshake,2,15);
        byte[] frame=hex(CODEC8E_FRAME);
        byte[] combined=new byte[handshake.length+frame.length];
        System.arraycopy(handshake,0,combined,0,handshake.length);
        System.arraycopy(frame,0,combined,handshake.length,frame.length);
        EmbeddedChannel channel=new EmbeddedChannel(new NettyProtocolDecoder());
        assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(combined)));
        assertArrayEquals(handshake,channel.readInbound());
        assertArrayEquals(frame,channel.readInbound());
        assertFalse(channel.finish());
    }

    private byte[] hex(String value) {
        byte[] bytes=new byte[value.length()/2];
        for(int i=0;i<bytes.length;i++) bytes[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16);
        return bytes;
    }
}
