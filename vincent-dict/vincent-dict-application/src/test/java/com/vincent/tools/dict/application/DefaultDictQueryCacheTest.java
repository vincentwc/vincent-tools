package com.vincent.tools.dict.application;

import com.vincent.tools.dict.application.port.DictCache;
import com.vincent.tools.dict.application.port.DictQueryRepository;
import com.vincent.tools.dict.application.port.NoopDictCache;
import com.vincent.tools.dict.domain.DictCode;
import com.vincent.tools.dict.domain.DictItemSource;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultDictQueryCacheTest {
    @Test
    void query_delegates_loading_to_cache_port() {
        CountingRepository repository = new CountingRepository();
        AnsweringCache cache = new AnsweringCache();
        cache.answer = Arrays.asList(item("CACHED"));
        DictQueryService service = new DefaultDictQueryService(
                repository, new SingleTenantProvider(), cache, DictLimits.defaults());

        assertThat(service.listEffectiveItems("ORDER_STATUS", "tenant-a"))
                .extracting(DictItemView::getCode)
                .containsExactly("CACHED");
        assertThat(repository.calls()).isZero();
    }

    @Test
    void noop_cache_executes_loader_once() {
        AtomicInteger calls = new AtomicInteger();
        List<DictItemView> result = new NoopDictCache().load(
                "ORDER_STATUS", "tenant-a", () -> {
                    calls.incrementAndGet();
                    return Arrays.asList(item("DB"));
                });
        assertThat(calls).hasValue(1);
        assertThat(result).extracting(DictItemView::getCode).containsExactly("DB");
    }

    private static DictItemView item(String code) {
        return new DictItemView(code, code + " name", code + " description", 1, DictItemSource.DEFAULT);
    }

    private static final class CountingRepository implements DictQueryRepository {
        private int calls;

        int calls() {
            return calls;
        }

        @Override
        public Optional<EffectiveDictData> findEffectiveData(DictCode dictCode, String tenantId) {
            calls++;
            return Optional.empty();
        }
    }

    private static final class AnsweringCache implements DictCache {
        List<DictItemView> answer;

        @Override
        public List<DictItemView> load(String dictCode, String tenantId,
                                       Supplier<List<DictItemView>> databaseLoader) {
            return answer;
        }

        @Override
        public void evictAll(String dictCode) {
        }

        @Override
        public void evictTenant(String dictCode, String tenantId) {
        }
    }
}
