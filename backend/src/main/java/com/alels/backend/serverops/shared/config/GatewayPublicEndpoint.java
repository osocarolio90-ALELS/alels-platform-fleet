package com.alels.backend.serverops.shared.config;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GatewayPublicEndpoint {
    private final String configuredHost;
    private final int port;

    public GatewayPublicEndpoint(
            @Value("${alels.gateway.public-host:}") String host,
            @Value("${alels.gateway.public-port:5050}") int port
    ) {
        this.configuredHost = host == null ? "" : host.trim();
        this.port = port;
    }

    public Optional<Endpoint> configured() {
        if (port < 1 || port > 65535) return Optional.empty();
        if (isUsableHost(configuredHost)) {
            return Optional.of(new Endpoint(configuredHost, port, "GATEWAY_PUBLIC_CONFIG"));
        }
        return detectServerIpv4()
                .map(host -> new Endpoint(host, port, "SERVER_NETWORK_INTERFACE"));
    }

    private static boolean isUsableHost(String value) {
        if (value == null || value.isBlank() || value.contains("/") || value.contains(" ")) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        return !normalized.equals("localhost") && !normalized.equals("127.0.0.1")
                && !normalized.equals("0.0.0.0") && !normalized.equals("::1");
    }

    private static Optional<String> detectServerIpv4() {
        List<AddressCandidate> candidates = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return Optional.empty();

            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) continue;

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address)
                            || address.isAnyLocalAddress()
                            || address.isLoopbackAddress()
                            || address.isLinkLocalAddress()) continue;
                    candidates.add(new AddressCandidate(
                            address.getHostAddress(),
                            address.isSiteLocalAddress(),
                            networkInterface.getIndex()
                    ));
                }
            }
        } catch (SocketException ignored) {
            return Optional.empty();
        }

        return candidates.stream()
                .sorted(Comparator.comparing(AddressCandidate::siteLocal).reversed()
                        .thenComparingInt(AddressCandidate::interfaceIndex)
                        .thenComparing(AddressCandidate::host))
                .map(AddressCandidate::host)
                .findFirst();
    }

    private record AddressCandidate(String host, boolean siteLocal, int interfaceIndex) {}

    public record Endpoint(String host, int port, String source) {}
}
