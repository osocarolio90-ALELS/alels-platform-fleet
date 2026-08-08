package com.alels.gateway.netty;

import com.alels.gateway.detector.ProtocolType;
import com.alels.gateway.server.ChannelType;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DeviceSessionRegistryTest {

    @Test
    void staleConnectionCloseCannotRemoveReplacementSession() {
        String imei = "999999999999991";
        EmbeddedChannel firstChannel = new EmbeddedChannel();
        EmbeddedChannel replacementChannel = new EmbeddedChannel();
        NettyCommandWriter first = new NettyCommandWriter(imei, firstChannel);
        NettyCommandWriter replacement = new NettyCommandWriter(imei, replacementChannel);

        DeviceSessionRegistry.registerOrUpdate(
                imei, ChannelType.GSM, ProtocolType.TELTONIKA_IMEI, "first", first
        );
        DeviceSessionRegistry.registerOrUpdate(
                imei, ChannelType.GSM, ProtocolType.TELTONIKA_CODEC8, "replacement", replacement
        );

        DeviceSessionRegistry.markChannelClosed(imei, ChannelType.GSM, first);

        assertEquals(ChannelType.GSM, DeviceSessionRegistry.getActiveNetwork(imei));
        assertSame(replacement, DeviceSessionRegistry.getActiveWriter(imei));

        DeviceSessionRegistry.markChannelClosed(imei, ChannelType.GSM, replacement);
        assertEquals(ChannelType.UNKNOWN, DeviceSessionRegistry.getActiveNetwork(imei));
        firstChannel.finishAndReleaseAll();
        replacementChannel.finishAndReleaseAll();
    }
}
