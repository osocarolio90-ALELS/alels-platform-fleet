package com.alels.backend.serverops.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.serverops.auth.dto.LoginRequest;
import com.alels.backend.serverops.auth.repository.AuthRepository;
import com.alels.backend.serverops.auth.repository.AuthRepository.AuthUserRow;
import com.alels.backend.serverops.shared.security.JwtService;

class AuthServiceSingleSessionTest {
    private AuthRepository repository;
    private JwtService jwtService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        repository=mock(AuthRepository.class);
        jwtService=mock(JwtService.class);
        PasswordEncoder encoder=mock(PasswordEncoder.class);
        service=new AuthService(repository,jwtService,encoder);
        when(repository.findActiveByEmail("user@example.test")).thenReturn(Optional.of(user()));
        when(encoder.matches("secret",user().passwordHash())).thenReturn(true);
    }

    @Test
    void rejectsLoginWhileAnotherBrowserLeaseIsActive() {
        when(repository.claimSingleSession(10L)).thenReturn(Optional.empty());

        ResponseStatusException exception=assertThrows(ResponseStatusException.class,
                ()->service.login(new LoginRequest("user@example.test","secret"),"127.0.0.1","browser-b"));

        assertEquals(409,exception.getStatusCode().value());
    }

    @Test
    void tokenUsesVersionReturnedByAtomicSessionClaim() {
        when(repository.claimSingleSession(10L)).thenReturn(Optional.of(8L));
        when(jwtService.generateToken(eq(10L),eq(20L),anyString(),anyString(),anyString(),anyString(),eq(8L))).thenReturn("token");

        service.login(new LoginRequest("user@example.test","secret"),"127.0.0.1","browser-a");

        verify(jwtService).generateToken(10L,20L,"CLIENTUSER","user@example.test","user","Test User",8L);
    }

    @Test
    void heartbeatCannotRefreshAReplacedSession() {
        when(repository.touchSession(10L,7L)).thenReturn(false);
        ResponseStatusException exception=assertThrows(ResponseStatusException.class,()->service.heartbeat(10L,7L));
        assertEquals(401,exception.getStatusCode().value());
    }

    private AuthUserRow user() {
        return new AuthUserRow(10L,20L,"Company","ACTIVE","user","Test User","user@example.test",
                "CLIENTUSER","ACTIVE","$2a$12$test",false,null,7L);
    }
}
