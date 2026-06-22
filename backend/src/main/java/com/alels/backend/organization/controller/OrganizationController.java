package com.alels.backend.organization.controller;

import static com.alels.backend.organization.dto.OrganizationDtos.CheckNameResponse;
import static com.alels.backend.organization.dto.OrganizationDtos.CompanyRegisterRequest;
import static com.alels.backend.organization.dto.OrganizationDtos.CompanyRegisterResponse;
import static com.alels.backend.organization.dto.OrganizationDtos.CompanyRow;
import static com.alels.backend.organization.dto.OrganizationDtos.CompanyUpdateRequest;
import static com.alels.backend.organization.dto.OrganizationDtos.OptionRow;
import static com.alels.backend.organization.dto.OrganizationDtos.UserRow;
import static com.alels.backend.organization.dto.OrganizationDtos.UserCreateRequest;
import static com.alels.backend.organization.dto.OrganizationDtos.UserUpdateRequest;
import static com.alels.backend.organization.dto.OrganizationDtos.WastedRow;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alels.backend.organization.service.OrganizationService;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@RestController
@RequestMapping({"/api/organization", "/api/organizations"})
public class OrganizationController {
    private final OrganizationService service;

    public OrganizationController(OrganizationService service) {
        this.service = service;
    }

    @GetMapping("/companies")
    public List<CompanyRow> companies(Authentication authentication) {
        return service.companies(user(authentication));
    }

    @GetMapping("/users")
    public List<UserRow> users(Authentication authentication) {
        return service.users(user(authentication));
    }

    @GetMapping("/wasted")
    public List<WastedRow> wasted(Authentication authentication) {
        return service.wasted(user(authentication));
    }

    @GetMapping("/parent-options")
    public List<OptionRow> parentOptions(Authentication authentication) {
        return service.parentOptions(user(authentication));
    }

    @GetMapping("/company-options")
    public List<OptionRow> companyOptions(Authentication authentication) {
        return service.companyOptions(user(authentication));
    }

    @GetMapping("/company/check-name")
    public CheckNameResponse checkName(
            @RequestParam String name,
            @RequestParam(required = false) Long excludeId
    ) {
        return service.checkName(name, excludeId);
    }

    @PostMapping("/company-registration")
    public CompanyRegisterResponse register(
            Authentication authentication,
            @RequestBody CompanyRegisterRequest request
    ) {
        return service.registerCompany(user(authentication), request);
    }

    @PutMapping("/companies/{id}")
    public Map<String, Object> updateCompany(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody CompanyUpdateRequest request
    ) {
        service.updateCompany(user(authentication), id, request);
        return Map.of("success", true);
    }

    @PostMapping("/users")
    public Map<String, Object> createUser(
            Authentication authentication,
            @RequestBody UserCreateRequest request
    ) {
        Long userId = service.createUser(user(authentication), request);
        return Map.of("success", true, "userId", userId);
    }

    @PutMapping("/users/{id}")
    public Map<String, Object> updateUser(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody UserUpdateRequest request
    ) {
        String generatedPassword = service.updateUser(user(authentication), id, request);
        if (generatedPassword == null || generatedPassword.isBlank()) {
            return Map.of("success", true);
        }
        return Map.of("success", true, "generatedPassword", generatedPassword);
    }

    @PostMapping("/companies/{id}/{action}")
    public Map<String, Object> companyAction(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable String action
    ) {
        service.companyAction(user(authentication), id, action);
        return Map.of("success", true);
    }

    @PostMapping("/users/{id}/{action}")
    public Map<String, Object> userAction(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable String action
    ) {
        service.userAction(user(authentication), id, action);
        return Map.of("success", true);
    }

    private JwtUserContext user(Authentication authentication) {
        return (JwtUserContext) authentication.getPrincipal();
    }
}
