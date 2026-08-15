package com.vincent.tools.dict.web;

import com.vincent.tools.dict.application.DictLimits;
import com.vincent.tools.dict.application.admin.DefaultDictAdminService;
import com.vincent.tools.dict.application.admin.DictAdminPermission;
import com.vincent.tools.dict.application.admin.DictAdminService;
import com.vincent.tools.dict.application.admin.OperatorProvider;
import com.vincent.tools.dict.application.admin.PageResult;
import com.vincent.tools.dict.application.admin.PermissionProvider;
import com.vincent.tools.dict.application.admin.TenantDirectory;
import com.vincent.tools.dict.application.admin.TenantOption;
import com.vincent.tools.dict.application.admin.query.DictPageQuery;
import com.vincent.tools.dict.application.admin.query.ItemPageQuery;
import com.vincent.tools.dict.application.admin.view.DictItemDetail;
import com.vincent.tools.dict.application.admin.view.DictSummary;
import com.vincent.tools.dict.application.port.DictAdminRepository;
import com.vincent.tools.dict.application.port.NoopDictCache;
import com.vincent.tools.dict.application.port.TxRunner;
import com.vincent.tools.dict.domain.Dict;
import com.vincent.tools.dict.domain.DictCode;
import com.vincent.tools.dict.domain.DictFactory;
import com.vincent.tools.dict.domain.DictItem;
import com.vincent.tools.dict.domain.DictItemFactory;
import com.vincent.tools.dict.domain.DictStatus;
import com.vincent.tools.dict.domain.ItemCode;
import com.vincent.tools.dict.domain.ItemCodeUsage;
import com.vincent.tools.dict.domain.ItemStatus;
import com.vincent.tools.dict.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DictAdminControllerTest {
    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String API = "/vincent/dict/admin/api/v1";

    private PermissionProvider permissionProvider;
    private OperatorProvider operatorProvider;
    private TenantDirectory tenantDirectory;
    private InMemoryDictAdminRepository repository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        permissionProvider = mock(PermissionProvider.class);
        operatorProvider = mock(OperatorProvider.class);
        tenantDirectory = mock(TenantDirectory.class);
        when(permissionProvider.hasPermission(any(DictAdminPermission.class), anyOptional()))
                .thenReturn(Boolean.TRUE);
        when(operatorProvider.currentOperatorId()).thenReturn("operator");
        when(tenantDirectory.exists(any(String.class))).thenReturn(Boolean.TRUE);
        when(tenantDirectory.search(nullable(String.class), anyInt(), anyInt()))
                .thenReturn(new PageResult<TenantOption>(
                        Collections.singletonList(new TenantOption("tenant-b", "Tenant B")), 1, 1, 20));
        repository = new InMemoryDictAdminRepository();
        mockMvc = mockMvc(tenantDirectory);
    }

    @Test
    void tenant_item_create_is_forbidden_when_permission_denied() throws Exception {
        when(permissionProvider.hasPermission(eq(DictAdminPermission.ITEM_CREATE), eq(Optional.of("tenant-b"))))
                .thenReturn(Boolean.FALSE);

        mockMvc.perform(post(API + "/dicts/10/items/tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"tenant-b\",\"code\":\"WAIT_CONFIRM\",\"name\":\"Waiting\",\"sortNo\":20}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        verify(permissionProvider).hasPermission(
                DictAdminPermission.ITEM_CREATE, Optional.of("tenant-b"));
    }

    @Test
    void lowercase_item_code_returns_invalid_argument() throws Exception {
        mockMvc.perform(post(API + "/dicts/10/items/default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"wait_confirm\",\"name\":\"Waiting\",\"sortNo\":20}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void lowercase_dict_code_returns_invalid_argument() throws Exception {
        mockMvc.perform(post(API + "/dicts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"order_type\",\"name\":\"Order type\",\"sortNo\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void page_size_greater_than_100_returns_invalid_argument() throws Exception {
        mockMvc.perform(get(API + "/dicts").param("page", "1").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void missing_operator_returns_invalid_argument() throws Exception {
        when(operatorProvider.currentOperatorId()).thenReturn("");

        mockMvc.perform(post(API + "/dicts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ORDER_TYPE\",\"name\":\"Order type\",\"sortNo\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void missing_tenant_directory_returns_configuration_invalid() throws Exception {
        mockMvc = mockMvc(null);

        mockMvc.perform(post(API + "/dicts/10/items/tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"tenant-b\",\"code\":\"WAIT_CONFIRM\",\"name\":\"Waiting\",\"sortNo\":20}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CONFIGURATION_INVALID"));

        mockMvc.perform(get(API + "/tenants").param("page", "1").param("size", "20"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("CONFIGURATION_INVALID"));
    }

    @Test
    void edit_deleted_item_returns_invalid_argument() throws Exception {
        mockMvc.perform(put(API + "/items/93")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Waiting confirm\",\"description\":\"Updated\",\"sortNo\":25}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void missing_dict_returns_not_found() throws Exception {
        mockMvc.perform(get(API + "/dicts/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DICT_NOT_FOUND"));
    }

    @Test
    void conflicting_dict_code_returns_conflict() throws Exception {
        repository.addExistingCode(DictCode.of("ORDER_STATUS"));

        mockMvc.perform(post(API + "/dicts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ORDER_STATUS\",\"name\":\"Dup\",\"sortNo\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DICT_CODE_CONFLICT"));
    }

    @Test
    void capabilities_report_permissions_and_directory_availability() throws Exception {
        when(permissionProvider.hasPermission(eq(DictAdminPermission.ITEM_CREATE), eq(Optional.of("tenant-b"))))
                .thenReturn(Boolean.FALSE);

        mockMvc.perform(get(API + "/capabilities").param("tenantId", "tenant-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.tenantDirectoryAvailable").value(true))
                .andExpect(jsonPath("$.data.permissions.DICT_VIEW").value(true))
                .andExpect(jsonPath("$.data.permissions.ITEM_CREATE").value(false));
    }

    @Test
    void capabilities_without_directory_hide_tenant_ui() throws Exception {
        mockMvc = mockMvc(null);

        mockMvc.perform(get(API + "/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantDirectoryAvailable").value(false));
    }

    @Test
    void spa_entry_requires_dict_view() throws Exception {
        when(permissionProvider.hasPermission(eq(DictAdminPermission.DICT_VIEW), eq(Optional.<String>empty())))
                .thenReturn(Boolean.FALSE);

        mockMvc.perform(get("/dict-admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mockMvc.perform(get("/dict-admin/"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void authorized_routes_return_component_api_response() throws Exception {
        mockMvc.perform(get(API + "/dicts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20));

        mockMvc.perform(post(API + "/dicts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ORDER_TYPE\",\"name\":\"Order type\",\"description\":\"\",\"sortNo\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100));

        mockMvc.perform(get(API + "/dicts/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("ORDER_STATUS"));

        mockMvc.perform(put(API + "/dicts/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Order lifecycle\",\"description\":\"Lifecycle\",\"sortNo\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(patch(API + "/dicts/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(API + "/dicts/10/items").param("tenantId", "tenant-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1));

        mockMvc.perform(post(API + "/dicts/10/items/default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WAIT_CONFIRM\",\"name\":\"Waiting\",\"sortNo\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(200));

        mockMvc.perform(post(API + "/dicts/10/items/tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"tenant-b\",\"code\":\"WAIT_PAY\",\"name\":\"Pay\",\"sortNo\":21}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(201));

        mockMvc.perform(put(API + "/items/90")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Waiting confirm\",\"description\":\"Updated\",\"sortNo\":25}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch(API + "/items/90/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete(API + "/items/91"))
                .andExpect(status().isOk());

        mockMvc.perform(post(API + "/items/92/restore"))
                .andExpect(status().isOk());

        mockMvc.perform(delete(API + "/dicts/10"))
                .andExpect(status().isOk());

        mockMvc.perform(post(API + "/dicts/11/restore"))
                .andExpect(status().isOk());

        mockMvc.perform(get(API + "/tenants").param("keyword", "ten").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/dict-admin"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/dict-admin/"))
                .andExpect(status().isOk());
    }

    private MockMvc mockMvc(TenantDirectory directory) {
        DictAdminService service = new DefaultDictAdminService(
                repository,
                new ImmediateTxRunner(),
                new NoopDictCache(),
                operatorProvider,
                permissionProvider,
                directory,
                DictLimits.defaults(),
                CLOCK);
        return MockMvcBuilders.standaloneSetup(
                        new DictAdminController(service, permissionProvider, directory),
                        new DictItemAdminController(service),
                        new TenantAdminController(directory, permissionProvider),
                        new DictAdminPageController(permissionProvider))
                .setControllerAdvice(new DictWebExceptionHandler())
                .setMessageConverters(jacksonConverter(), new ResourceHttpMessageConverter())
                .addPlaceholderValue("vincent.dict.admin.api-path", API)
                .addPlaceholderValue("vincent.dict.admin.base-path", "/dict-admin")
                .build();
    }

    private static MappingJackson2HttpMessageConverter jacksonConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new MappingJackson2HttpMessageConverter(mapper);
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> anyOptional() {
        return any(Optional.class);
    }

    private static final class ImmediateTxRunner implements TxRunner {
        @Override
        public <T> T required(Supplier<T> action) {
            return action.get();
        }
    }

    private static final class InMemoryDictAdminRepository implements DictAdminRepository {
        private final Map<Long, Dict> dicts = new LinkedHashMap<Long, Dict>();
        private final Map<Long, DictItem> items = new LinkedHashMap<Long, DictItem>();
        private final List<DictCode> existingCodes = new ArrayList<DictCode>();
        private long nextDictId = 100L;
        private long nextItemId = 200L;

        private InMemoryDictAdminRepository() {
            replace(dict(10L, "ORDER_STATUS", false));
            replace(dict(11L, "PAY_STATUS", true));
            put(item(90L, TenantId.defaultItem(), false));
            put(item(91L, TenantId.of("tenant-a"), false));
            put(item(92L, TenantId.of("tenant-a"), true));
            put(item(93L, TenantId.of("tenant-a"), true));
        }

        private void addExistingCode(DictCode code) {
            existingCodes.add(code);
        }

        private void replace(Dict dict) {
            dicts.put(dict.id(), dict);
        }

        private void put(DictItem item) {
            items.put(item.id(), item);
        }

        @Override
        public PageResult<DictSummary> pageDicts(DictPageQuery query) {
            return new PageResult<DictSummary>(Collections.<DictSummary>emptyList(), 0, query.getPage(), query.getSize());
        }

        @Override
        public Optional<Dict> findDict(long dictId) {
            return Optional.ofNullable(copyDict(dicts.get(dictId)));
        }

        @Override
        public Optional<Dict> lockDict(long dictId) {
            return Optional.ofNullable(copyDict(dicts.get(dictId)));
        }

        @Override
        public boolean existsDictCode(DictCode code) {
            return existingCodes.contains(code);
        }

        @Override
        public long insertDict(Dict dict) {
            long id = nextDictId++;
            dicts.put(id, dict);
            return id;
        }

        @Override
        public void updateDict(Dict dict) {
            dicts.put(dict.id(), dict);
        }

        @Override
        public PageResult<DictItemDetail> pageItems(long dictId, ItemPageQuery query) {
            return new PageResult<DictItemDetail>(
                    Collections.<DictItemDetail>emptyList(), 0, query.getPage(), query.getSize());
        }

        @Override
        public Optional<DictItem> findItem(long itemId) {
            return Optional.ofNullable(copyItem(items.get(itemId)));
        }

        @Override
        public ItemCodeUsage findItemCodeUsage(long dictId, ItemCode code, TenantId tenantId) {
            return ItemCodeUsage.none();
        }

        @Override
        public int countUndeletedItems(long dictId) {
            return 0;
        }

        @Override
        public int countUndeletedItems(long dictId, TenantId tenantId) {
            return 0;
        }

        @Override
        public long insertItem(DictItem item) {
            long id = nextItemId++;
            items.put(id, item);
            return id;
        }

        @Override
        public void updateItem(DictItem item) {
            items.put(item.id(), item);
        }

        private static Dict dict(long id, String code, boolean deleted) {
            return DictFactory.rebuild(id, DictCode.of(code), "Order status", "", DictStatus.ENABLED, 10,
                    3, deleted, "operator", NOW, "operator", NOW);
        }

        private static DictItem item(long id, TenantId tenantId, boolean deleted) {
            return DictItemFactory.rebuild(id, 10L, ItemCode.of("WAIT_CONFIRM"), "Waiting", tenantId, "",
                    ItemStatus.ENABLED, 20, 1, deleted, "operator", NOW, "operator", NOW);
        }

        private static Dict copyDict(Dict dict) {
            if (dict == null) {
                return null;
            }
            return DictFactory.rebuild(dict.id(), dict.code(), dict.name(), dict.description(), dict.status(),
                    dict.sortNo(), dict.version(), dict.isDeleted(), dict.createdBy(), dict.createdAt(),
                    dict.updatedBy(), dict.updatedAt());
        }

        private static DictItem copyItem(DictItem item) {
            if (item == null) {
                return null;
            }
            return DictItemFactory.rebuild(item.id(), item.dictId(), item.code(), item.name(), item.tenantId(),
                    item.description(), item.status(), item.sortNo(), item.version(), item.isDeleted(),
                    item.createdBy(), item.createdAt(), item.updatedBy(), item.updatedAt());
        }
    }
}
