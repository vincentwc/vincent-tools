package com.vincent.tools.region.application.port;

import com.vincent.tools.region.application.RegionView;

import java.util.List;
import java.util.Optional;

public interface RegionRepository {
    Optional<RegionView> findByCode(String code);

    List<RegionView> listChildren(String parentCode);
}
