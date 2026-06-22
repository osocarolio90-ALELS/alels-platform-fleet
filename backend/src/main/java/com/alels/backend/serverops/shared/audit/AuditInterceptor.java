package com.alels.backend.serverops.shared.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.alels.backend.serverops.shared.security.JwtUserContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuditInterceptor implements HandlerInterceptor {
    private final AuditLogService auditLogService;

    public AuditInterceptor(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return;
        }
        if (!request.getRequestURI().startsWith("/api/server-monitor/")) {
            return;
        }
        if (response.getStatus() >= 200 && response.getStatus() < 400) {
            JwtUserContext user = currentUser();
            auditLogService.logServerOpsRead(
                    user == null ? null : user.userId(),
                    user == null ? null : user.companyId(),
                    request.getRequestURI(),
                    request.getMethod(),
                    response.getStatus(),
                    clientIp(request)
            );
        }
    }

    private JwtUserContext currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserContext user)) {
            return null;
        }
        return user;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor;
        }
        return request.getRemoteAddr();
    }
}
