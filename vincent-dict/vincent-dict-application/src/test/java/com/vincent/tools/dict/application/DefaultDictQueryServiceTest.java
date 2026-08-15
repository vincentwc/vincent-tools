package com.vincent.tools.dict.application;

import com.vincent.tools.dict.application.port.DictQueryRepository;
import com.vincent.tools.dict.application.port.DictCache;
import com.vincent.tools.dict.application.port.NoopDictCache;
import com.vincent.tools.host.TenantProvider;
import com.vincent.tools.dict.domain.DictCode;
import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;
import com.vincent.tools.dict.domain.DictItemSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultDictQueryServiceTest {
    @Test
    void lists_current_tenant_snapshot_in_sort_number_code_and_persisted_id_order() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(ApplicationFixtures.DICT_CODE, ApplicationFixtures.TENANT_A, ApplicationFixtures.enabled(
                ApplicationFixtures.item(3L, "B", 10, DictItemSource.DEFAULT),
                ApplicationFixtures.item(2L, "A", 10, DictItemSource.TENANT),
                ApplicationFixtures.item(1L, "A", 10, DictItemSource.DEFAULT),
                ApplicationFixtures.item(4L, "FIRST", 9, DictItemSource.DEFAULT)));

        DictQueryService service = service(repository, () -> Optional.of(ApplicationFixtures.TENANT_A));

        List<DictItemView> items = service.listEffectiveItems(ApplicationFixtures.DICT_CODE);

        assertThat(items)
                .extracting(DictItemView::getCode)
                .containsExactly("FIRST", "A", "A", "B");
        assertThat(items.get(1).getSource()).isEqualTo(DictItemSource.DEFAULT);
        assertThat(items.get(0).getName()).isEqualTo("FIRST name");
        assertThat(items.get(0).getDescription()).isEqualTo("FIRST description");
        assertThat(items.get(0).getSortNo()).isEqualTo(9);
    }

    @Test
    void lists_explicit_tenant_snapshot() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(ApplicationFixtures.DICT_CODE, ApplicationFixtures.TENANT_B,
                ApplicationFixtures.enabled(ApplicationFixtures.item(1L, "B_ONLY", 1, DictItemSource.TENANT)));

        assertThat(service(repository).listEffectiveItems(ApplicationFixtures.DICT_CODE, ApplicationFixtures.TENANT_B))
                .extracting(DictItemView::getCode)
                .containsExactly("B_ONLY");
    }

    @Test
    void finds_current_tenant_item_from_the_list_snapshot() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(ApplicationFixtures.DICT_CODE, ApplicationFixtures.TENANT_A, ApplicationFixtures.enabled(
                ApplicationFixtures.item(1L, "FOUND", 1, DictItemSource.DEFAULT),
                ApplicationFixtures.item(2L, "OTHER", 2, DictItemSource.TENANT)));
        DictQueryService service = service(repository, () -> Optional.of(ApplicationFixtures.TENANT_A));

        assertThat(service.findEffectiveItem(ApplicationFixtures.DICT_CODE, "FOUND"))
                .map(DictItemView::getCode)
                .contains("FOUND");
        assertThat(service.findEffectiveItem(ApplicationFixtures.DICT_CODE, "MISSING")).isEmpty();
    }

    @Test
    void finds_explicit_tenant_item_from_the_list_snapshot() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(ApplicationFixtures.DICT_CODE, ApplicationFixtures.TENANT_B,
                ApplicationFixtures.enabled(ApplicationFixtures.item(1L, "FOUND", 1, DictItemSource.TENANT)));

        assertThat(service(repository).findEffectiveItem(ApplicationFixtures.DICT_CODE, "FOUND", ApplicationFixtures.TENANT_B))
                .map(DictItemView::getCode)
                .contains("FOUND");
    }

    @Test
    void single_tenant_provider_uses_default_only_sentinel_without_domain_tenant_validation() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(ApplicationFixtures.DICT_CODE, "0",
                ApplicationFixtures.enabled(ApplicationFixtures.item(1L, "DEFAULT", 1, DictItemSource.DEFAULT)));

        assertThat(service(repository, new SingleTenantProvider()).listEffectiveItems(ApplicationFixtures.DICT_CODE))
                .extracting(DictItemView::getCode)
                .containsExactly("DEFAULT");
    }

    @Test
    void ordinary_tenant_provider_cannot_use_reserved_zero() {
        assertThatThrownBy(() -> service(new InMemoryRepository(), () -> Optional.of("0"))
                .listEffectiveItems(ApplicationFixtures.DICT_CODE))
                .isInstanceOf(DictException.class)
                .extracting("code")
                .isEqualTo(DictErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void missing_current_tenant_context_throws_tenant_context_missing() {
        assertThatThrownBy(() -> service(new InMemoryRepository(), () -> Optional.<String>empty())
                .listEffectiveItems(ApplicationFixtures.DICT_CODE))
                .isInstanceOf(DictException.class)
                .extracting("code")
                .isEqualTo(DictErrorCode.TENANT_CONTEXT_MISSING);
    }

    @Test
    void invalid_current_tenant_context_uses_normal_tenant_validation() {
        assertThatThrownBy(() -> service(new InMemoryRepository(), () -> Optional.of("  "))
                .listEffectiveItems(ApplicationFixtures.DICT_CODE))
                .isInstanceOf(DictException.class)
                .extracting("code")
                .isEqualTo(DictErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void explicit_tenant_rejects_reserved_zero() {
        assertThatThrownBy(() -> service(new InMemoryRepository())
                .listEffectiveItems(ApplicationFixtures.DICT_CODE, "0"))
                .isInstanceOf(DictException.class)
                .extracting("code")
                .isEqualTo(DictErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void disabled_dict_returns_empty() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(ApplicationFixtures.DICT_CODE, "0", ApplicationFixtures.disabled());
        DictQueryService service = service(repository, new SingleTenantProvider());

        assertThat(service.listEffectiveItems(ApplicationFixtures.DICT_CODE)).isEmpty();
    }

    @Test
    void missing_dict_throws_dict_not_found() {
        assertThatThrownBy(() -> service(new InMemoryRepository(), new SingleTenantProvider())
                .listEffectiveItems("MISSING"))
                .isInstanceOf(DictException.class)
                .extracting("code")
                .isEqualTo(DictErrorCode.DICT_NOT_FOUND);
    }

    @Test
    void returns_an_unmodifiable_snapshot_even_when_cache_returns_a_mutable_list() {
        List<DictItemView> cached = new ArrayList<DictItemView>();
        cached.add(new DictItemView("ONE", "One", "First", 1, DictItemSource.DEFAULT));
        DictQueryService service = new DefaultDictQueryService(new FailingRepository(), new SingleTenantProvider(),
                new StaticCache(cached), DictLimits.defaults());

        List<DictItemView> result = service.listEffectiveItems(ApplicationFixtures.DICT_CODE);
        cached.clear();

        assertThat(result).extracting(DictItemView::getCode).containsExactly("ONE");
        assertThatThrownBy(() -> result.add(result.get(0))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void effective_data_defensively_copies_and_exposes_an_unmodifiable_snapshot() {
        List<EffectiveItemData> source = new ArrayList<EffectiveItemData>();
        EffectiveItemData original = ApplicationFixtures.item(1L, "ONE", 1, DictItemSource.DEFAULT);
        source.add(original);

        EffectiveDictData data = new EffectiveDictData(true, source);
        source.clear();

        assertThat(data.getItems()).containsExactly(original);
        assertThatThrownBy(() -> data.getItems().add(original)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void accepts_exactly_two_thousand_effective_items() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(ApplicationFixtures.DICT_CODE, "0", new EffectiveDictData(true, ApplicationFixtures.items(2000)));

        assertThat(service(repository, new SingleTenantProvider()).listEffectiveItems(ApplicationFixtures.DICT_CODE))
                .hasSize(2000);
    }

    @Test
    void rejects_more_than_two_thousand_effective_items() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(ApplicationFixtures.DICT_CODE, "0", new EffectiveDictData(true, ApplicationFixtures.items(2001)));

        assertThatThrownBy(() -> service(repository, new SingleTenantProvider())
                .listEffectiveItems(ApplicationFixtures.DICT_CODE))
                .isInstanceOf(DictException.class)
                .extracting("code")
                .isEqualTo(DictErrorCode.CONFIGURATION_INVALID);
    }

    @Test
    void rejects_non_positive_limit_configuration() {
        for (int invalidLimit : Arrays.asList(0, -1)) {
            assertThatThrownBy(() -> new DictLimits(invalidLimit))
                    .isInstanceOf(DictException.class)
                    .extracting("code")
                    .isEqualTo(DictErrorCode.CONFIGURATION_INVALID);
        }
    }

    @Test
    void default_limit_is_exactly_two_thousand() {
        assertThat(DictLimits.defaults().getMaxEffectiveItems()).isEqualTo(2000);
    }

    @Test
    void database_loader_queries_repository_once() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(ApplicationFixtures.DICT_CODE, "0", ApplicationFixtures.enabled());

        service(repository, new SingleTenantProvider()).listEffectiveItems(ApplicationFixtures.DICT_CODE);

        assertThat(repository.getFindInvocations()).isEqualTo(1);
    }

    @Test
    void find_filters_the_list_returned_by_cache() {
        DictItemView cachedItem = new DictItemView("FOUND", "Found", "Cached", 1, DictItemSource.DEFAULT);
        DictQueryService service = new DefaultDictQueryService(new FailingRepository(), new SingleTenantProvider(),
                new StaticCache(Collections.singletonList(cachedItem)), DictLimits.defaults());

        assertThat(service.findEffectiveItem(ApplicationFixtures.DICT_CODE, "FOUND")).contains(cachedItem);
        assertThat(service.findEffectiveItem(ApplicationFixtures.DICT_CODE, "MISSING")).isEmpty();
    }

    @Test
    void noop_cache_calls_loader_once_and_returns_its_unmodified_result() {
        AtomicInteger invocations = new AtomicInteger();
        List<DictItemView> loaded = Collections.emptyList();

        List<DictItemView> result = new NoopDictCache().load(ApplicationFixtures.DICT_CODE, "0", () -> {
            invocations.incrementAndGet();
            return loaded;
        });

        assertThat(invocations).hasValue(1);
        assertThat(result).isSameAs(loaded);
    }

    @Test
    void noop_cache_propagates_loader_exception_unchanged() {
        IllegalStateException failure = new IllegalStateException("database unavailable");

        assertThatThrownBy(() -> new NoopDictCache().load(ApplicationFixtures.DICT_CODE, "0", () -> {
            throw failure;
        })).isSameAs(failure);
    }

    private static DictQueryService service(InMemoryRepository repository) {
        return service(repository, new SingleTenantProvider());
    }

    private static DictQueryService service(InMemoryRepository repository, TenantProvider tenantProvider) {
        return new DefaultDictQueryService(repository, tenantProvider, new NoopDictCache(), DictLimits.defaults());
    }

    private static final class InMemoryRepository implements DictQueryRepository {
        private final Map<String, EffectiveDictData> dataByDictAndTenant = new HashMap<String, EffectiveDictData>();
        private int findInvocations;

        void save(String dictCode, String tenantId, EffectiveDictData data) {
            dataByDictAndTenant.put(key(dictCode, tenantId), data);
        }

        @Override
        public Optional<EffectiveDictData> findEffectiveData(DictCode dictCode, String tenantId) {
            findInvocations++;
            return Optional.ofNullable(dataByDictAndTenant.get(key(dictCode.value(), tenantId)));
        }

        int getFindInvocations() {
            return findInvocations;
        }

        private static String key(String dictCode, String tenantId) {
            return dictCode + ":" + tenantId;
        }
    }

    private static final class FailingRepository implements DictQueryRepository {
        @Override
        public Optional<EffectiveDictData> findEffectiveData(DictCode dictCode, String tenantId) {
            throw new AssertionError("repository must not be queried");
        }
    }

    private static final class StaticCache implements DictCache {
        private final List<DictItemView> items;

        private StaticCache(List<DictItemView> items) {
            this.items = items;
        }

        @Override
        public List<DictItemView> load(String dictCode, String tenantId,
                                       Supplier<List<DictItemView>> databaseLoader) {
            return items;
        }

        @Override
        public void evictAll(String dictCode) {
        }

        @Override
        public void evictTenant(String dictCode, String tenantId) {
        }
    }
}
