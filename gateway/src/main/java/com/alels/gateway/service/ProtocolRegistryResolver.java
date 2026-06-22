package com.alels.gateway.service;

import com.alels.gateway.detector.ProtocolType;
import com.alels.gateway.model.ProtocolInfo;
import com.alels.gateway.repository.ProtocolRegistryRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProtocolRegistryResolver {

    private static final ProtocolRegistryRepository repository =
            new ProtocolRegistryRepository();

    private static final Map<String, ProtocolInfo> cache =
            new ConcurrentHashMap<>();

    private ProtocolRegistryResolver() {
    }

    public static ProtocolInfo resolve(ProtocolType protocolType) {

        if (protocolType == null) {
            return null;
        }

        return resolve(protocolType.name());
    }

    public static ProtocolInfo resolve(String protocolCode) {

        if (protocolCode == null || protocolCode.isBlank()) {
            return null;
        }

        String normalizedCode =
                protocolCode.trim().toUpperCase();

        ProtocolInfo cached =
                cache.get(normalizedCode);

        if (cached != null) {
            return cached;
        }

        ProtocolInfo info =
                repository.findByProtocolCode(normalizedCode);

        if (info == null) {
            System.out.println(
                    "[PROTOCOL REGISTRY] NOT FOUND protocol="
                            + normalizedCode
            );
            return null;
        }

        cache.put(normalizedCode, info);

        System.out.println(
                "[PROTOCOL REGISTRY] protocol="
                        + info.getProtocolCode()
                        + " brand="
                        + info.getBrandCode()
                        + " parser="
                        + info.getParserCode()
                        + " status="
                        + info.getStatus()
        );

        return info;
    }

    public static boolean isActive(ProtocolType protocolType) {

        ProtocolInfo info =
                resolve(protocolType);

        return info != null && info.isActive();
    }

    public static void clearCache() {
        cache.clear();
        System.out.println("[PROTOCOL REGISTRY] cache cleared");
    }
}