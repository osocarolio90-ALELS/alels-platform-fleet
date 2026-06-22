package com.alels.backend.user.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.user.dto.UserProfileDtos.UserProfileResponse;
import com.alels.backend.user.repository.UserProfileRepository;

@Service
public class UserProfileService {
    private static final long MAX_PHOTO_BYTES = 2_000_000L;
    private static final int AVATAR_SIZE = 512;

    private final UserProfileRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Path uploadDir;

    public UserProfileService(
            UserProfileRepository repository,
            PasswordEncoder passwordEncoder,
            @Value("${alels.upload.profile-photo-dir:uploads/profile-photos}") String uploadDir
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
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
            newPhotoContentType = safeContentType(photo.getContentType());
            validatePhoto(photo, newPhotoContentType);
            newPhotoPath = savePhoto(actor.userId(), photo, newPhotoContentType);
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

    private String savePhoto(Long userId, MultipartFile photo, String contentType) {
        try {
            Files.createDirectories(uploadDir);
            String extension = extension(contentType);
            Path destination = uploadDir.resolve("user-" + userId + "-" + UUID.randomUUID() + extension).normalize();
            if (!destination.startsWith(uploadDir)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path upload tidak valid.");
            }
            writeSquarePhoto(photo, destination, contentType);
            return "/api/user/profile/photo/" + destination.getFileName();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Gagal menyimpan foto profile.");
        }
    }

    private void validatePhoto(MultipartFile photo, String contentType) {
        if (!contentType.equals("image/jpeg") && !contentType.equals("image/png")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File profile harus JPG atau PNG.");
        }
        if (photo.getSize() > MAX_PHOTO_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ukuran foto maksimum 2 MB setelah crop.");
        }
    }

    private void writeSquarePhoto(MultipartFile photo, Path destination, String contentType) throws IOException {
        BufferedImage source;
        try (InputStream inputStream = photo.getInputStream()) {
            source = ImageIO.read(inputStream);
        }
        if (source == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File gambar tidak valid atau rusak.");
        }

        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int side = Math.min(sourceWidth, sourceHeight);
        int sourceX = Math.max(0, (sourceWidth - side) / 2);
        int sourceY = Math.max(0, (sourceHeight - side) / 2);
        int imageType = contentType.equals("image/png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(AVATAR_SIZE, AVATAR_SIZE, imageType);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, 0, 0, AVATAR_SIZE, AVATAR_SIZE, sourceX, sourceY, sourceX + side, sourceY + side, null);
        graphics.dispose();

        String format = contentType.equals("image/png") ? "png" : "jpg";
        if (!ImageIO.write(target, format, destination.toFile())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Format gambar tidak didukung.");
        }
    }

    private void deleteQuietly(String storedPath) {
        String fileName = storedPath.substring(storedPath.lastIndexOf('/') + 1);
        try {
            Path file = uploadDir.resolve(fileName).normalize();
            if (file.startsWith(uploadDir)) Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }

    private void deleteNewPhotoIfUpdateFailed(String storedPath) {
        if (storedPath != null && !storedPath.isBlank()) deleteQuietly(storedPath);
    }

    private String safeContentType(String contentType) {
        String clean = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        if (clean.equals("image/jpg")) return "image/jpeg";
        return clean;
    }

    private String extension(String contentType) {
        return contentType.equals("image/png") ? ".png" : ".jpg";
    }

    private String detailsJson(List<String> fields) {
        String joined = fields.stream().map(field -> "\"" + field + "\"").reduce((a, b) -> a + "," + b).orElse("");
        return "{\"fields\":[" + joined + "]}";
    }
}
