package com.alels.backend.serverops.shared.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class RootPasswordBootstrapper implements ApplicationRunner {
    private static final String TEMP_PASSWORD_MARKER = "TEMP_OWNER_PASSWORD";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String initialPassword;

    public RootPasswordBootstrapper(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${alels.bootstrap.root-password:Alels@2026!}") String initialPassword
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.initialPassword = initialPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        String encodedPassword = passwordEncoder.encode(initialPassword);
        jdbcTemplate.update("""
                UPDATE users
                SET password_hash = ?, must_change_password = TRUE, updated_at = NOW()
                WHERE username = 'alels-root' AND password_hash = ?
                """, encodedPassword, TEMP_PASSWORD_MARKER);
    }
}
