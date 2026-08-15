package com.vincent.tools.region.infra.mybatis;

import com.vincent.tools.region.application.RegionView;
import com.vincent.tools.region.application.port.RegionRepository;
import com.vincent.tools.region.infra.mybatis.mapper.RegionMapper;
import com.vincent.tools.region.infra.mybatis.po.RegionPo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MybatisRegionRepository implements RegionRepository {
    private final RegionMapper regionMapper;

    public MybatisRegionRepository(RegionMapper regionMapper) {
        this.regionMapper = regionMapper;
    }

    @Override
    public Optional<RegionView> findByCode(String code) {
        RegionPo po = regionMapper.selectByCode(code);
        return po == null ? Optional.<RegionView>empty() : Optional.of(toView(po));
    }

    @Override
    public List<RegionView> listChildren(String parentCode) {
        List<RegionPo> rows = regionMapper.selectByParentCode(parentCode);
        List<RegionView> views = new ArrayList<RegionView>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            views.add(toView(rows.get(index)));
        }
        return views;
    }

    private static RegionView toView(RegionPo po) {
        return new RegionView(po.getCode(), po.getName(), po.getLevel(), po.getParentCode());
    }
}
