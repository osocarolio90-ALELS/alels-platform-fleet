package com.alels.gateway.config;

import java.util.Properties;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;

public final class KafkaSecurityConfig {
    private KafkaSecurityConfig() {
    }

    public static void apply(Properties properties) {
        String protocol = env("ALELS_KAFKA_SECURITY_PROTOCOL", "PLAINTEXT").toUpperCase();
        properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, protocol);
        if (protocol.startsWith("SASL_")) {
            properties.put(SaslConfigs.SASL_MECHANISM, required("ALELS_KAFKA_SASL_MECHANISM"));
            properties.put(SaslConfigs.SASL_JAAS_CONFIG, required("ALELS_KAFKA_SASL_JAAS_CONFIG"));
        }
        optional(properties, SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, "ALELS_KAFKA_SSL_TRUSTSTORE_LOCATION");
        optional(properties, SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, "ALELS_KAFKA_SSL_TRUSTSTORE_PASSWORD");
        optional(properties, SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, "ALELS_KAFKA_SSL_KEYSTORE_LOCATION");
        optional(properties, SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, "ALELS_KAFKA_SSL_KEYSTORE_PASSWORD");
        optional(properties, SslConfigs.SSL_KEY_PASSWORD_CONFIG, "ALELS_KAFKA_SSL_KEY_PASSWORD");
    }

    private static void optional(Properties properties, String key, String environment) {
        String value = System.getenv(environment);
        if (value != null && !value.isBlank()) properties.put(key, value.trim());
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be configured");
        return value.trim();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
