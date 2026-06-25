package com.alels.backend.masterdata.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.masterdata.dto.MasterWastedDtos.MasterWastedRow;
import com.alels.backend.masterdata.repository.MasterWastedRepository;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@Service
public class MasterWastedService {
    private final MasterWastedRepository repository;

    public MasterWastedService(MasterWastedRepository repository) { this.repository = repository; }

    public List<MasterWastedRow> list(JwtUserContext user, String itemType) { assertCanView(user); return repository.findWasted(itemType); }
    public void restore(JwtUserContext user, String itemType, Long id) { assertCanEdit(user); repository.restore(itemType, id, user.userId()); }
    public void permanentDelete(JwtUserContext user, String itemType, Long id) { assertCanEdit(user); repository.permanentDelete(itemType, id); }

    private void assertCanView(JwtUserContext user) { if (user == null || user.userId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized."); }
    private void assertCanEdit(JwtUserContext user) { String role = user == null ? "" : user.normalizedRole(); if (!"SUPERADMIN".equals(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hanya SUPERADMIN yang bisa mengubah Wasted Master Data."); }
}
