package com.alels.backend.serverops.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.serverops.auth.dto.LoginRequest;
import com.alels.backend.serverops.auth.dto.LoginResponse;
import com.alels.backend.serverops.auth.dto.LoginResponse.UserSession;
import com.alels.backend.serverops.auth.repository.AuthRepository;
import com.alels.backend.serverops.auth.repository.AuthRepository.AuthUserRow;
import com.alels.backend.serverops.shared.security.JwtService;

@Service
public class AuthService {
    private final AuthRepository authRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthRepository authRepository, JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.authRepository = authRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        String email = request.email() == null ? "" : request.email().trim().toLowerCase();
        String password = request.password() == null ? "" : request.password();

        AuthUserRow user = authRepository.findActiveByEmail(email)
                .orElseThrow(() -> unauthorized(email, ipAddress, userAgent, "USER_NOT_FOUND"));

        if (!"ACTIVE".equalsIgnoreCase(user.status())) {
            authRepository.insertLoginEvent(user.id(), user.companyId(), email, false, ipAddress, userAgent, "USER_INACTIVE");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not active");
        }
        if (user.companyStatus() != null && !"ACTIVE".equalsIgnoreCase(user.companyStatus()) && !"PROVISION".equalsIgnoreCase(user.companyStatus())) {
            authRepository.insertLoginEvent(user.id(), user.companyId(), email, false, ipAddress, userAgent, "COMPANY_SUSPENDED");
            throw new ResponseStatusException(HttpStatus.LOCKED, "Akun atau company sedang disuspend. Harap segera menghubungi owner/perusahaan utama.");
        }

        if (!isPasswordValid(password, user.passwordHash())) {
            authRepository.insertLoginEvent(user.id(), user.companyId(), email, false, ipAddress, userAgent, "INVALID_PASSWORD");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        long sessionVersion=authRepository.claimSingleSession(user.id()).orElseThrow(()->{
            authRepository.insertLoginEvent(user.id(),user.companyId(),email,false,ipAddress,userAgent,"SESSION_ALREADY_ACTIVE");
            return new ResponseStatusException(HttpStatus.CONFLICT,"User sedang aktif di browser atau perangkat lain. Logout atau tutup browser aktif, lalu tunggu 5 detik.");
        });
        authRepository.updateLastLoginAt(user.id());
        authRepository.activateProvisionCompanyOnFirstLogin(user.companyId());
        authRepository.insertLoginEvent(user.id(), user.companyId(), email, true, ipAddress, userAgent, null);

        String token = jwtService.generateToken(user.id(), user.companyId(), user.role(), user.email(), user.username(), user.fullName(), sessionVersion);
        return new LoginResponse(
                true,
                user.mustChangePassword() ? "Login success. Password change is required." : "Login success",
                token,
                new UserSession(user.id(), user.companyId(), user.companyName(), user.username(), user.fullName(), user.email(), user.role(), user.status(), user.profilePhotoUrl())
        );
    }

    public void logout(Long userId, Long companyId, String email, String ipAddress, String userAgent) {
        if (userId == null) return;
        authRepository.revokeSessions(userId);
        authRepository.insertLogoutEvent(userId, companyId, email, ipAddress, userAgent);
    }

    public void heartbeat(Long userId,long sessionVersion) {
        if (userId==null||!authRepository.touchSession(userId,sessionVersion)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Session is no longer active");
        }
    }

    private boolean isPasswordValid(String rawPassword, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
            return passwordEncoder.matches(rawPassword, storedHash);
        }
        return false;
    }

    private ResponseStatusException unauthorized(String email, String ipAddress, String userAgent, String reason) {
        authRepository.insertLoginEvent(null, null, email, false, ipAddress, userAgent, reason);
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
