package com.alels.backend.serverops.shared.security;

import java.io.IOException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;

    public JwtAuthenticationFilter(JwtService jwtService, JdbcTemplate jdbcTemplate) {
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractBearerToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtUserContext user = jwtService.parseToken(token);
                if (!isSessionAllowed(user)) {
                    SecurityContextHolder.clearContext();
                    response.setStatus(423);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Akun atau company sedang disuspend. Harap segera menghubungi owner/perusahaan utama.\"}");
                    return;
                }
                String role = user.normalizedRole();
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isSessionAllowed(JwtUserContext user) {
        if (user.userId() == null) return false;
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM users u
                LEFT JOIN companies c ON c.id = u.company_id
                WHERE u.id = ?
                  AND u.deleted_at IS NULL
                  AND UPPER(COALESCE(u.status, '')) = 'ACTIVE'
                  AND (c.id IS NULL OR (c.deleted_at IS NULL AND UPPER(COALESCE(c.status, '')) IN ('ACTIVE', 'PROVISION')))
                """, Integer.class, user.userId());
        return count != null && count == 1;
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
