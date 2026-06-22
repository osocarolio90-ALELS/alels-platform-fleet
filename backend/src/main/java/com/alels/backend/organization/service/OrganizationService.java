package com.alels.backend.organization.service;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.organization.dto.OrganizationDtos.CheckNameResponse;
import com.alels.backend.organization.dto.OrganizationDtos.CompanyRegisterRequest;
import com.alels.backend.organization.dto.OrganizationDtos.CompanyRegisterResponse;
import com.alels.backend.organization.dto.OrganizationDtos.CompanyRow;
import com.alels.backend.organization.dto.OrganizationDtos.CompanyUpdateRequest;
import com.alels.backend.organization.dto.OrganizationDtos.OptionRow;
import com.alels.backend.organization.dto.OrganizationDtos.UserCreateRequest;
import com.alels.backend.organization.dto.OrganizationDtos.UserRow;
import com.alels.backend.organization.dto.OrganizationDtos.UserUpdateRequest;
import com.alels.backend.organization.dto.OrganizationDtos.WastedRow;
import com.alels.backend.organization.repository.OrganizationRepository;
import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.serverops.shared.security.RoleNormalizer;

@Service
public class OrganizationService {
    private final OrganizationRepository repository;
    private final PasswordEncoder passwordEncoder;

    public OrganizationService(OrganizationRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<CompanyRow> companies(JwtUserContext actor) {
        requireOrganizationAccess(actor);
        return repository.findCompanies(actor.userId(), actor.companyId(), role(actor));
    }

    public List<UserRow> users(JwtUserContext actor) {
        requireOrganizationAccess(actor);
        return repository.findUsers(actor.companyId(), role(actor));
    }

    public List<WastedRow> wasted(JwtUserContext actor) {
        requireOrganizationAccess(actor);
        return repository.findWasted(actor.companyId(), role(actor));
    }

    public List<OptionRow> parentOptions(JwtUserContext actor) {
        requireOrganizationAccess(actor);
        return repository.parentOptions(actor.companyId(), role(actor));
    }

    public List<OptionRow> companyOptions(JwtUserContext actor) {
        requireOrganizationAccess(actor);
        return repository.companyOptions(actor.companyId(), role(actor));
    }

    public CheckNameResponse checkName(String name, Long excludeId) {
        return new CheckNameResponse(repository.companyNameExists(name == null ? "" : name.trim(), excludeId));
    }

    public CompanyRegisterResponse registerCompany(JwtUserContext actor, CompanyRegisterRequest request) {
        requireOrganizationAccess(actor);
        String actorRole = role(actor);
        String targetRole = normalizeRole(request.adminRole());
        if (!isRoleCreatable(actorRole, targetRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role target tidak diizinkan untuk user login saat ini.");
        }

        boolean adminSpecial = "SUPERADMIN".equals(actorRole) && "ADMIN".equals(targetRole);
        Long rootCompanyId = repository.rootCompanyId().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "ALELS root company tidak ditemukan."));
        Long companyId;
        String companyName;
        String status;

        if (adminSpecial) {
            companyId = rootCompanyId;
            companyName = "ALELS TECH INDONESIA";
            status = "ACTIVE";
        } else {
            companyName = upper(request.companyName());
            if (companyName.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company wajib diisi.");
            if (repository.companyNameExists(companyName, null)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Nama company sudah ada di sistem global.");
            Long parentCompanyId = resolveParentCompany(actor, request.parentCompanyId());
            if (!repository.canAccessCompany(parentCompanyId, actor.companyId(), actorRole)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Parent company tidak berada dalam line akses user login.");
            }
            String plan = upper(defaultString(request.plan(), "SAMPLE"));
            Integer monthPacket = "SAMPLE".equals(plan) ? 1 : request.monthPacket();
            Long storageMb = "SAMPLE".equals(plan) ? 100L : Math.max(0L, request.storageSize() == null ? 0L : request.storageSize());
            companyId = repository.createCompany(parentCompanyId, companyName, defaultString(request.companyType(), request.type()), plan, monthPacket, storageMb, request.country(), actor.userId());
            status = "PROVISION";
        }

        String email = defaultString(request.adminEmail(), request.userEmail()).trim().toLowerCase(Locale.ROOT);
        String fullName = defaultString(request.adminFullName(), request.username()).trim();
        String username = defaultString(request.username(), fullName).trim();
        if (email.isBlank() || username.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username dan email wajib diisi.");
        if (repository.userEmailExists(email, null)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email user sudah ada di sistem global.");
        if (request.temporaryPassword() == null || request.temporaryPassword().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password wajib digenerate.");

        Long userId = repository.createUser(companyId, username, fullName, email, targetRole, passwordEncoder.encode(request.temporaryPassword()), actor.userId());
        repository.log(actor.userId(), actor.companyId(), "COMPANY", companyId, adminSpecial ? "CREATE_INTERNAL_ADMIN" : "CREATE_COMPANY", "{\"status\":\"" + status + "\"}");
        repository.log(actor.userId(), actor.companyId(), "USER", userId, "CREATE_USER", "{\"role\":\"" + targetRole + "\"}");
        return new CompanyRegisterResponse(companyId, userId, companyName, email, targetRole, request.temporaryPassword(), status);
    }

    public void updateCompany(JwtUserContext actor, Long id, CompanyUpdateRequest request) {
        requireCanAccessCompany(actor, id);
        requireNotOwnCompany(actor, id, "Company sendiri tidak boleh diedit dari Company List. Gunakan menu User Profile atau recovery procedure jika diperlukan.");
        requireNotRootCompany(id, "Root company ALELS tidak boleh diedit dari Company List.");
        String companyName = upper(request.companyName());
        if (companyName.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nama company wajib diisi.");
        if (repository.companyNameExists(companyName, id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Nama company sudah ada di sistem global.");
        Long parentCompanyId = request.parentCompanyId();
        if (parentCompanyId != null) {
            if (!repository.canAccessCompany(parentCompanyId, actor.companyId(), role(actor))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Parent company tidak berada dalam line akses user login.");
            if (repository.isParentCycle(id, parentCompanyId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent company tidak boleh menjadi diri sendiri atau child company.");
        }
        repository.updateCompany(id, companyName, parentCompanyId);
        repository.log(actor.userId(), actor.companyId(), "COMPANY", id, "UPDATE_COMPANY", "{\"fields\":[\"companyName\",\"parentCompanyId\"]}");
    }

    public Long createUser(JwtUserContext actor, UserCreateRequest request) {
        requireOrganizationAccess(actor);
        String actorRole = role(actor);
        String targetRole = normalizeRole(request.role());
        if (!isRoleCreatable(actorRole, targetRole)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role target tidak diizinkan untuk user login saat ini.");
        Long targetCompanyId = request.companyId() == null ? actor.companyId() : request.companyId();
        if (targetCompanyId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company user wajib diisi.");
        if (!repository.canAccessCompany(targetCompanyId, actor.companyId(), actorRole)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Company target tidak berada dalam line akses user login.");
        String email = request.email() == null ? "" : request.email().trim().toLowerCase(Locale.ROOT);
        String username = request.username() == null ? "" : request.username().trim();
        String fullName = defaultString(request.fullName(), username).trim();
        if (email.isBlank() || username.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username dan email wajib diisi.");
        if (request.temporaryPassword() == null || request.temporaryPassword().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password wajib digenerate.");
        if (repository.userEmailExists(email, null)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email user sudah ada di sistem global.");
        Long userId = repository.createUser(targetCompanyId, username, fullName, email, targetRole, passwordEncoder.encode(request.temporaryPassword()), actor.userId());
        repository.log(actor.userId(), actor.companyId(), "USER", userId, "CREATE_USER", "{\"role\":\"" + targetRole + "\"}");
        return userId;
    }

    public String updateUser(JwtUserContext actor, Long id, UserUpdateRequest request) {
        requireNotSelfUser(actor, id, "Akun sendiri tidak boleh diedit dari User List. Gunakan menu User Profile.");
        Long targetCompanyId = repository.companyIdForUser(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User tidak ditemukan."));
        requireCanAccessCompany(actor, targetCompanyId);
        String targetRole = repository.roleForUser(id).map(this::normalizeRole).orElse("");
        if (!canManageTargetRole(role(actor), targetRole)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role target tidak boleh diedit oleh user login.");
        String email = request.email() == null ? "" : request.email().trim().toLowerCase(Locale.ROOT);
        String username = request.username() == null ? "" : request.username().trim();
        String fullName = defaultString(request.fullName(), username).trim();
        if (email.isBlank() || username.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username dan email wajib diisi.");
        if (repository.userEmailExists(email, id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email user sudah ada di sistem global.");
        String generatedPassword = request.temporaryPassword() == null ? "" : request.temporaryPassword().trim();
        String hash = generatedPassword.isBlank() ? null : passwordEncoder.encode(generatedPassword);
        repository.updateUser(id, username, fullName, email, hash);
        repository.log(actor.userId(), actor.companyId(), "USER", id, "UPDATE_USER", hash == null ? "{\"fields\":[\"profile\"]}" : "{\"fields\":[\"profile\",\"password\"]}");
        return generatedPassword.isBlank() ? null : generatedPassword;
    }

    public void companyAction(JwtUserContext actor, Long id, String action) {
        requireCanAccessCompany(actor, id);
        String normalizedAction = normalizeAction(action);
        if (isDestructiveCompanyAction(normalizedAction)) {
            requireNotOwnCompany(actor, id, "Company sendiri tidak boleh diproses dari Company List.");
            requireNotRootCompany(id, "Root company ALELS tidak boleh dihapus, disuspend, atau permanent delete.");
        }
        switch (normalizedAction) {
            case "delete" -> repository.softDeleteCompany(id, actor.userId(), "Deleted by " + actor.email());
            case "suspend" -> repository.suspendCompany(id, actor.userId());
            case "activate", "restore" -> repository.activateCompany(id);
            case "permanent-delete" -> {
                requirePermanentDelete(actor);
                repository.permanentDeleteCompany(id);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action tidak dikenal.");
        }
        repository.log(actor.userId(), actor.companyId(), "COMPANY", id, normalizedAction.toUpperCase(Locale.ROOT), "{}");
    }

    public void userAction(JwtUserContext actor, Long id, String action) {
        requireNotSelfUser(actor, id, "Akun sendiri tidak boleh diproses dari User List.");
        Long targetCompanyId = repository.companyIdForUser(id).orElse(actor.companyId());
        requireCanAccessCompany(actor, targetCompanyId);
        String targetRole = repository.roleForUser(id).map(this::normalizeRole).orElse("");
        if (!canManageTargetRole(role(actor), targetRole)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role target tidak boleh diproses oleh user login.");
        String normalizedAction = normalizeAction(action);
        if (isDestructiveUserAction(normalizedAction)) {
            requireNotRootSuperadmin(id, "Root superadmin tidak boleh dihapus, disuspend, atau permanent delete.");
        }
        switch (normalizedAction) {
            case "delete" -> repository.softDeleteUser(id, actor.userId(), "Deleted by " + actor.email());
            case "suspend" -> repository.suspendUser(id, actor.userId());
            case "activate", "restore" -> repository.activateUser(id);
            case "permanent-delete" -> {
                requirePermanentDelete(actor);
                repository.permanentDeleteUser(id);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action tidak dikenal.");
        }
        repository.log(actor.userId(), actor.companyId(), "USER", id, normalizedAction.toUpperCase(Locale.ROOT), "{}");
    }


    private void requireNotSelfUser(JwtUserContext actor, Long targetUserId, String message) {
        if (actor.userId() != null && actor.userId().equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private void requireNotOwnCompany(JwtUserContext actor, Long targetCompanyId, String message) {
        if (actor.companyId() != null && actor.companyId().equals(targetCompanyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private void requireNotRootCompany(Long companyId, String message) {
        if (repository.isRootCompany(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private void requireNotRootSuperadmin(Long userId, String message) {
        if (repository.isRootSuperadmin(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private boolean isDestructiveCompanyAction(String action) {
        return List.of("delete", "suspend", "permanent-delete").contains(action);
    }

    private boolean isDestructiveUserAction(String action) {
        return List.of("delete", "suspend", "permanent-delete").contains(action);
    }

    private String normalizeAction(String action) {
        return action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
    }
    private Long resolveParentCompany(JwtUserContext actor, Long requestedParentId) {
        String actorRole = role(actor);
        if ("SUPERADMIN".equals(actorRole) || "ADMIN".equals(actorRole)) {
            return requestedParentId == null ? repository.rootCompanyId().orElse(actor.companyId()) : requestedParentId;
        }
        if ("OWNER".equals(actorRole) || "MANAGER".equals(actorRole)) return actor.companyId();
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role tidak boleh membuat company.");
    }

    private void requireCanAccessCompany(JwtUserContext actor, Long companyId) {
        requireOrganizationAccess(actor);
        if (!repository.canAccessCompany(companyId, actor.companyId(), role(actor))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Akses company ditolak.");
    }

    private void requireOrganizationAccess(JwtUserContext actor) {
        String role = role(actor);
        if (!List.of("SUPERADMIN", "ADMIN", "OWNER", "MANAGER").contains(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role tidak memiliki akses Organization.");
    }

    private void requirePermanentDelete(JwtUserContext actor) {
        String role = role(actor);
        if (!"SUPERADMIN".equals(role) && !"OWNER".equals(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permanent delete hanya untuk Superadmin dan Owner.");
    }

    private boolean isRoleCreatable(String actorRole, String targetRole) {
        return switch (actorRole) {
            case "SUPERADMIN" -> List.of("SUPERADMIN", "ADMIN", "OWNER", "MANAGER", "TECHUSER", "CLIENTUSER").contains(targetRole);
            case "ADMIN" -> List.of("OWNER", "MANAGER", "TECHUSER", "CLIENTUSER").contains(targetRole);
            case "OWNER" -> List.of("MANAGER", "TECHUSER", "CLIENTUSER").contains(targetRole);
            case "MANAGER" -> List.of("TECHUSER", "CLIENTUSER").contains(targetRole);
            default -> false;
        };
    }

    private boolean canManageTargetRole(String actorRole, String targetRole) {
        if (targetRole == null || targetRole.isBlank()) return false;
        return switch (actorRole) {
            case "SUPERADMIN" -> true;
            case "ADMIN" -> !List.of("SUPERADMIN").contains(targetRole);
            case "OWNER" -> List.of("MANAGER", "TECHUSER", "CLIENTUSER").contains(targetRole);
            case "MANAGER" -> List.of("TECHUSER", "CLIENTUSER").contains(targetRole);
            default -> false;
        };
    }

    private String role(JwtUserContext actor) { return normalizeRole(actor.role()); }
    private String normalizeRole(String role) { return RoleNormalizer.normalize(role); }
    private String upper(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private String defaultString(String primary, String fallback) { return primary == null || primary.isBlank() ? (fallback == null ? "" : fallback) : primary; }
}
