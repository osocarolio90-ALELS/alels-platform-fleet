package com.alels.backend.serverops.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.alels.backend.serverops.shared.audit.AuditInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @NonNull
    private final AuditInterceptor auditInterceptor;

    public WebConfig(@NonNull AuditInterceptor auditInterceptor) {
        this.auditInterceptor = auditInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(auditInterceptor).addPathPatterns("/api/server-monitor/**");
    }
}
