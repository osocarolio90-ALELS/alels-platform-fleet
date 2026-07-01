package com.alels.backend.user.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.shared.storage.SquarePhotoStorageService;
import com.alels.backend.user.dto.UserProfileDtos.UserProfileResponse;
import com.alels.backend.user.repository.UserProfileRepository;

@Service
public class UserProfileService {
    private static final long MAX_PHOTO_BYTES = 2_000_000L;

    private final UserProfileRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Path uploadDir;
    private final SquarePhotoStorageService photoStorage;

    public UserProfileService(
            UserProfileRepository repository,
            PasswordEncoder passwordEncoder,
            SquarePhotoStorageService photoStorage,
            @Value("${alels.upload.profile-photo-dir:uploads/profile-photos}") String uploadDir
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.photoStorage = photoStorage;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public UserProfileResponse profile(JwtUserContext actor) {
        return repository.findProfile(actor.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile user tidak ditemukan."));
    }

    public UserProfileResponse updateProfile(
            JwtUserContext actor,
            String username,
            String email,
            String newPassword,
            MultipartFile photo
    ) {
        UserProfileResponse current = profile(actor);
        String cleanUsername = normalizeText(username, current.username());
        String cleanEmail = normalizeEmail(email, current.email());
        List<String> changedFields = new ArrayList<>();

        boolean usernameChanged = !equalsIgnoreCase(cleanUsername, current.username());
        boolean emailChanged = !equalsIgnoreCase(cleanEmail, current.email());

        if (usernameChanged && repository.usernameExists(cleanUsername, actor.userId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username user sudah ada di sistem global.");
        }
        if (emailChanged && repository.emailExists(cleanEmail, actor.userId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email user sudah ada di sistem global.");
        }

        String newPhotoPath = null;
        String newPhotoContentType = null;
        if (photo != null && !photo.isEmpty()) {
            var storedPhoto = photoStorage.store(uploadDir, "user-" + actor.userId(), photo, MAX_PHOTO_BYTES);
            newPhotoContentType = storedPhoto.contentType();
            newPhotoPath = "/api/user/profile/photo/" + storedPhoto.fileName();
            changedFields.add("photo");
        }

        try {
            if (usernameChanged || emailChanged || newPhotoPath != null) {
                repository.updateProfile(
                        actor.userId(),
                        usernameChanged ? cleanUsername : null,
                        usernameChanged ? cleanUsername : null,
                        emailChanged ? cleanEmail : null,
                        newPhotoPath,
                        newPhotoContentType
                );
                if (usernameChanged) changedFields.add("username");
                if (emailChanged) changedFields.add("email");
            }

            if (newPassword != null && !newPassword.isBlank()) {
                if (newPassword.length() < 8) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password baru minimum 8 karakter.");
                }
                repository.updatePassword(actor.userId(), passwordEncoder.encode(newPassword));
                changedFields.add("password");
            }
        } catch (DataIntegrityViolationException ex) {
            deleteNewPhotoIfUpdateFailed(newPhotoPath);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username atau email sudah digunakan user lain.");
        } catch (RuntimeException ex) {
            deleteNewPhotoIfUpdateFailed(newPhotoPath);
            throw ex;
        }

        if (newPhotoPath != null) {
            repository.findPreviousPhotoPath(actor.userId(), newPhotoPath)
                    .filter(old -> old != null && !old.isBlank())
                    .ifPresent(this::deleteQuietly);
        }

        if (!changedFields.isEmpty()) {
            repository.logBestEffort(actor.userId(), actor.companyId(), "UPDATE_PROFILE", detailsJson(changedFields));
        }
        return profile(actor);
    }

    private String normalizeText(String value, String fallback) {
        String clean = value == null ? "" : value.trim();
        if (clean.isBlank()) return fallback;
        return clean;
    }

    private String normalizeEmail(String value, String fallback) {
        String clean = value == null ? "" : value.trim();
        if (clean.isBlank()) return fallback;
        return clean.toLowerCase(Locale.ROOT);
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null) return right == null;
        return left.equalsIgnoreCase(right == null ? "" : right);
    }

    private void deleteQuietly(String storedPath) {
        photoStorage.deleteQuietly(uploadDir, storedPath);
    }

    private void deleteNewPhotoIfUpdateFailed(String storedPath) {
        if (storedPath != null && !storedPath.isBlank()) deleteQuietly(storedPath);
    }

    private String detailsJson(List<String> fields) {
        String joined = fields.stream().map(field -> "\"" + field + "\"").reduce((a, b) -> a + "," + b).orElse("");
        return "{\"fields\":[" + joined + "]}";
    }
}
