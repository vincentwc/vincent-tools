package com.vincent.tools.region.application;

import java.util.List;
import java.util.Optional;

public interface RegionQueryService {
    Optional<RegionView> findByCode(String code);

    List<RegionView> listChildren(String parentCode);
}
