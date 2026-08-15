package com.vincent.tools.region.application;

import com.vincent.tools.region.application.port.RegionRepository;
import com.vincent.tools.region.domain.RegionFieldLimits;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class DefaultRegionQueryService implements RegionQueryService {
    private final RegionRepository repository;

    public DefaultRegionQueryService(RegionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public Optional<RegionView> findByCode(String code) {
        return repository.findByCode(requireCode(code));
    }

    @Override
    public List<RegionView> listChildren(String parentCode) {
        return repository.listChildren(normalizeParentCode(parentCode));
    }

    private static String requireCode(String code) {
        return RegionFieldLimits.requireNonBlank(code, "code", RegionFieldLimits.MAX_CODE_LENGTH);
    }

    private static String normalizeParentCode(String parentCode) {
        if (parentCode == null || parentCode.trim().isEmpty() || "0".equals(parentCode.trim())) {
            return "0";
        }
        return requireCode(parentCode);
    }
}
