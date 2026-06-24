package com.alels.backend.masterdata.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceCountryCreateRequest;
import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceCountryRow;
import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceProviderUpdateRequest;
import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceProviderUpdateResponse;
import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceRow;
import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceUpdateRequest;
import com.alels.backend.assetregister.service.EnergyReferenceService;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@Service
public class ReferencePriceService {
    private final EnergyReferenceService delegate;
    private final MasterOptionService guard;

    public ReferencePriceService(EnergyReferenceService delegate, MasterOptionService guard) {
        this.delegate = delegate;
        this.guard = guard;
    }

    public List<EnergyReferenceRow> list(JwtUserContext user, String countryCode) {
        guard.assertCanView(user);
        return delegate.list(user, countryCode);
    }

    public List<EnergyReferenceCountryRow> countries(JwtUserContext user) {
        guard.assertCanView(user);
        return delegate.countries(user);
    }

    public void createCountry(JwtUserContext user, EnergyReferenceCountryCreateRequest request) {
        guard.assertCanEdit(user);
        delegate.createCountry(user, request);
    }

    public EnergyReferenceProviderUpdateResponse updateProvider(JwtUserContext user, EnergyReferenceProviderUpdateRequest request) {
        guard.assertCanView(user);
        return delegate.updateProvider(user, request);
    }

    public void updateManual(JwtUserContext user, Long id, EnergyReferenceUpdateRequest request) {
        guard.assertCanEdit(user);
        delegate.updateManual(user, id, request);
    }

    public void delete(JwtUserContext user, Long id) {
        guard.assertCanEdit(user);
        delegate.delete(user, id);
    }
}
