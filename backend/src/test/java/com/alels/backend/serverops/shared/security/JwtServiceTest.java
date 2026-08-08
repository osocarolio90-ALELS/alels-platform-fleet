package com.alels.backend.serverops.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtServiceTest {

    @Test
    void tokenCarriesSessionVersionAndTenantClaims() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(
                service,
                "jwtSecret",
                "test-only-secret-that-is-longer-than-thirty-two-bytes"
        );
        ReflectionTestUtils.setField(service, "expirationMs", 60_000L);

        String token = service.generateToken(
                10L, 20L, "CLIENTUSER", "user@example.test", "user", "Test User", 7L
        );
        JwtUserContext parsed = service.parseToken(token);

        assertEquals(10L, parsed.userId());
        assertEquals(20L, parsed.companyId());
        assertEquals("CLIENTUSER", parsed.normalizedRole());
        assertEquals(7L, parsed.sessionVersion());
    }
}
