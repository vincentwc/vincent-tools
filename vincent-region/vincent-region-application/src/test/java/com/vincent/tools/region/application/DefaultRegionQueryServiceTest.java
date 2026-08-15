package com.vincent.tools.region.application;

import com.vincent.tools.region.application.port.RegionRepository;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultRegionQueryServiceTest {
    @Test
    void list_children_normalizes_root_parent_code() {
        InMemoryRegionRepository repository = new InMemoryRegionRepository();
        repository.seed(
                region("110000", "北京市", 1, "0"),
                region("440000", "广东省", 1, "0"));
        RegionQueryService service = new DefaultRegionQueryService(repository);

        assertThat(service.listChildren(null)).hasSize(2);
        assertThat(service.listChildren("")).hasSize(2);
        assertThat(service.listChildren("0")).hasSize(2);
    }

    @Test
    void find_by_code_returns_matching_region() {
        InMemoryRegionRepository repository = new InMemoryRegionRepository();
        repository.seed(region("440103", "荔湾区", 3, "440100"));
        RegionQueryService service = new DefaultRegionQueryService(repository);

        Optional<RegionView> found = service.findByCode("440103");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("荔湾区");
    }

    private static RegionView region(String code, String name, int level, String parentCode) {
        return new RegionView(code, name, level, parentCode);
    }

    private static final class InMemoryRegionRepository implements RegionRepository {
        private List<RegionView> regions = Arrays.asList();

        void seed(RegionView... values) {
            regions = Arrays.asList(values);
        }

        @Override
        public Optional<RegionView> findByCode(String code) {
            for (int index = 0; index < regions.size(); index++) {
                RegionView region = regions.get(index);
                if (region.getCode().equals(code)) {
                    return Optional.of(region);
                }
            }
            return Optional.empty();
        }

        @Override
        public List<RegionView> listChildren(String parentCode) {
            List<RegionView> children = new java.util.ArrayList<RegionView>();
            for (int index = 0; index < regions.size(); index++) {
                RegionView region = regions.get(index);
                if (parentCode.equals(region.getParentCode())) {
                    children.add(region);
                }
            }
            return children;
        }
    }
}
