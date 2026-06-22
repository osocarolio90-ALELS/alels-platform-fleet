package com.alels.backend.serverops.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    @Value("${alels.jwt.secret}")
    private String jwtSecret;

    @Value("${alels.jwt.expiration-ms}")
    private long expirationMs;

    public String generateToken(Long userId, Long companyId, String role, String email, String username, String fullName) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("companyId", companyId)
                .claim("role", role)
                .claim("username", username)
                .claim("fullName", fullName)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(getSigningKey())
                .compact();
    }

    public JwtUserContext parseToken(String token) {
        Claims claims = Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
        Long userId = numberToLong(claims.get("userId"));
        Long companyId = numberToLong(claims.get("companyId"));
        String role = claims.get("role", String.class);
        return new JwtUserContext(userId, companyId, role, claims.getSubject());
    }

    private Long numberToLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}
