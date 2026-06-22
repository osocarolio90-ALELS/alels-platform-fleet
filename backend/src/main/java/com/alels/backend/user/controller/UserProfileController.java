package com.alels.backend.user.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.user.dto.UserProfileDtos.UserProfileResponse;
import com.alels.backend.user.dto.UserProfileDtos.UserProfileUpdateResponse;
import com.alels.backend.user.service.UserProfileService;

@RestController
@RequestMapping("/api/user/profile")
public class UserProfileController {
    private final UserProfileService service;
    private final Path uploadDir;

    public UserProfileController(
            UserProfileService service,
            @Value("${alels.upload.profile-photo-dir:uploads/profile-photos}") String uploadDir
    ) {
        this.service = service;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @GetMapping
    public UserProfileResponse profile(Authentication authentication) {
        return service.profile(user(authentication));
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserProfileUpdateResponse update(
            Authentication authentication,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String newPassword,
            @RequestParam(required = false) MultipartFile photo
    ) {
        UserProfileResponse updated = service.updateProfile(user(authentication), username, email, newPassword, photo);
        return new UserProfileUpdateResponse(true, updated);
    }

    @GetMapping("/photo/{fileName:.+}")
    public ResponseEntity<Resource> photo(@PathVariable String fileName) throws IOException {
        Path file = uploadDir.resolve(fileName).normalize();
        if (!file.startsWith(uploadDir) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new UrlResource(file.toUri());
        String contentType = Files.probeContentType(file);
        MediaType mediaType = contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().cachePrivate())
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFileName() + "\"")
                .body(resource);
    }

    private JwtUserContext user(Authentication authentication) {
        return (JwtUserContext) authentication.getPrincipal();
    }
}
