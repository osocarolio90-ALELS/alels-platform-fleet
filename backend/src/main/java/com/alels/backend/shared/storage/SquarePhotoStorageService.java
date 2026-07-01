package com.alels.backend.shared.storage;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SquarePhotoStorageService {
    private static final int OUTPUT_SIZE = 512;
    private static final Set<String> SUPPORTED_TYPES = Set.of("image/jpeg", "image/png");

    public StoredPhoto store(Path directory, String prefix, MultipartFile photo, long maximumBytes) {
        String contentType = normalizeContentType(photo == null ? null : photo.getContentType());
        validate(photo, contentType, maximumBytes);
        try {
            Path normalizedDirectory = directory.toAbsolutePath().normalize();
            Files.createDirectories(normalizedDirectory);
            String fileName = prefix + "-" + UUID.randomUUID() + extension(contentType);
            Path destination = normalizedDirectory.resolve(fileName).normalize();
            if (!destination.startsWith(normalizedDirectory)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path upload tidak valid.");
            }
            writeSquarePhoto(photo, destination, contentType);
            return new StoredPhoto(fileName, contentType);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Gagal menyimpan foto.");
        }
    }

    public Resource load(Path directory, String fileName) throws IOException {
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        Path file = normalizedDirectory.resolve(fileName).normalize();
        if (!file.startsWith(normalizedDirectory) || !Files.isRegularFile(file)) return null;
        return new UrlResource(file.toUri());
    }

    public String contentType(Resource resource) throws IOException {
        String value = Files.probeContentType(resource.getFile().toPath());
        return value == null ? "application/octet-stream" : value;
    }

    public void deleteQuietly(Path directory, String storedReference) {
        if (storedReference == null || storedReference.isBlank()) return;
        String fileName = storedReference.substring(storedReference.lastIndexOf('/') + 1);
        try {
            Path normalizedDirectory = directory.toAbsolutePath().normalize();
            Path file = normalizedDirectory.resolve(fileName).normalize();
            if (file.startsWith(normalizedDirectory)) Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Cleanup is best effort; the database reference remains authoritative.
        }
    }

    private void validate(MultipartFile photo, String contentType, long maximumBytes) {
        if (photo == null || photo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File foto wajib diisi.");
        }
        if (!SUPPORTED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File foto harus JPG atau PNG.");
        }
        if (photo.getSize() > maximumBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ukuran foto melebihi batas.");
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
        int side = Math.min(source.getWidth(), source.getHeight());
        int sourceX = Math.max(0, (source.getWidth() - side) / 2);
        int sourceY = Math.max(0, (source.getHeight() - side) / 2);
        int imageType = "image/png".equals(contentType) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(OUTPUT_SIZE, OUTPUT_SIZE, imageType);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE, sourceX, sourceY, sourceX + side, sourceY + side, null);
        graphics.dispose();
        if (!ImageIO.write(target, "image/png".equals(contentType) ? "png" : "jpg", destination.toFile())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Format gambar tidak didukung.");
        }
    }

    private String normalizeContentType(String value) {
        String contentType = value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
        return "image/jpg".equals(contentType) ? "image/jpeg" : contentType;
    }

    private String extension(String contentType) {
        return "image/png".equals(contentType) ? ".png" : ".jpg";
    }

    public record StoredPhoto(String fileName, String contentType) {}
}
