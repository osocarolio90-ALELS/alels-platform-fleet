package com.alels.backend.assetregister.controller;

import java.util.List;
import java.util.Map;
import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.Resource;

import com.alels.backend.assetregister.dto.DriverRegisterDtos.DriverLookupOption;
import com.alels.backend.assetregister.dto.DriverRegisterDtos.DriverRegisterRequest;
import com.alels.backend.assetregister.dto.DriverRegisterDtos.DriverRegisterRow;
import com.alels.backend.assetregister.service.DriverRegisterService;
import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.shared.storage.SquarePhotoStorageService;

@RestController
@RequestMapping("/api/asset-register/drivers")
public class DriverRegisterController {
    private final DriverRegisterService service;
    private final SquarePhotoStorageService photoStorage;

    public DriverRegisterController(DriverRegisterService service, SquarePhotoStorageService photoStorage) {
        this.service = service;
        this.photoStorage = photoStorage;
    }

    @GetMapping public List<DriverRegisterRow> list(Authentication auth) { return service.list(user(auth)); }
    @GetMapping("/company-options") public List<DriverLookupOption> companyOptions(Authentication auth) { return service.companyOptions(user(auth)); }
    @PostMapping public Map<String, Object> create(Authentication auth, @RequestBody DriverRegisterRequest request) { return Map.of("success", true, "id", service.create(user(auth), request)); }
    @PutMapping("/{id}") public Map<String, Object> update(Authentication auth, @PathVariable Long id, @RequestBody DriverRegisterRequest request) { service.update(user(auth), id, request); return Map.of("success", true); }
    @DeleteMapping("/{id}") public Map<String, Object> delete(Authentication auth, @PathVariable Long id) { service.delete(user(auth), id); return Map.of("success", true); }
    @PutMapping("/{id}/suspend") public Map<String, Object> suspend(Authentication auth, @PathVariable Long id) { service.setStatus(user(auth), id, "SUSPENDED"); return Map.of("success", true); }
    @PutMapping("/{id}/activate") public Map<String, Object> activate(Authentication auth, @PathVariable Long id) { service.setStatus(user(auth), id, "ACTIVE"); return Map.of("success", true); }
    @PutMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> updatePhoto(Authentication auth, @PathVariable Long id, @RequestParam("photo") MultipartFile photo) {
        String fileName = service.updatePhoto(user(auth), id, photo);
        return Map.of("success", true, "photoUrl", "/api/asset-register/drivers/photo/" + fileName);
    }
    @DeleteMapping("/{id}/photo")
    public Map<String, Object> removePhoto(Authentication auth, @PathVariable Long id) {
        service.removePhoto(user(auth), id);
        return Map.of("success", true);
    }
    @GetMapping("/photo/{fileName:.+}")
    public ResponseEntity<Resource> photo(@PathVariable String fileName) throws IOException {
        Resource resource = photoStorage.load(service.uploadDir(), fileName);
        if (resource == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().cachePrivate())
                .contentType(MediaType.parseMediaType(photoStorage.contentType(resource)))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .body(resource);
    }

    private JwtUserContext user(Authentication auth) { return (JwtUserContext) auth.getPrincipal(); }
}
