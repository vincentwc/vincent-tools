package com.vincent.tools.dict.example;

import com.vincent.tools.dict.application.DictItemView;
import com.vincent.tools.dict.application.DictQueryService;
import com.vincent.tools.dict.application.admin.DictAdminService;
import com.vincent.tools.dict.application.admin.command.UpdateItemCommand;
import com.vincent.tools.dict.application.admin.query.DictPageQuery;
import com.vincent.tools.dict.application.admin.query.ItemPageQuery;
import com.vincent.tools.dict.application.admin.view.DictItemDetail;
import com.vincent.tools.dict.application.port.DictCache;
import com.vincent.tools.dict.cache.redis.RedisDictCache;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DictRedisIntegrationIT {
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2.5-alpine"))
                    .withExposedPorts(6379)
                    .withStartupTimeout(Duration.ofMinutes(2));

    private ConfigurableApplicationContext contextA;
    private ConfigurableApplicationContext contextB;

    @BeforeAll
    static void startSharedInfrastructure() {
        ExampleMysqlSupport.ensureStarted();
        REDIS.start();
    }

    @AfterAll
    static void stopRedis() {
        REDIS.stop();
    }

    @BeforeEach
    void startDualContexts() {
        contextA = startContext();
        contextB = startContext();
        assertThat(contextA.getBean(DictCache.class)).isInstanceOf(RedisDictCache.class);
        assertThat(contextB.getBean(DictCache.class)).isInstanceOf(RedisDictCache.class);
    }

    @AfterEach
    void restoreDemoItemsAndCloseContexts() {
        try {
            if (contextA != null && contextA.isActive()) {
                restoreDemoItemNames(admin(contextA));
            }
        } finally {
            closeQuietly(contextA);
            closeQuietly(contextB);
            contextA = null;
            contextB = null;
        }
    }

    @Test
    void tenant_item_update_on_a_is_visible_on_b_without_waiting_ttl() {
        DictQueryService queryB = query(contextB);
        assertThat(queryB.listEffectiveItems("ORDER_STATUS", "tenant-a"))
                .extracting(DictItemView::getCode)
                .contains("WAIT_CONFIRM");
        assertThat(itemName(queryB, "tenant-a", "WAIT_CONFIRM")).isEqualTo("Wait Confirm");

        DictItemDetail tenantItem = requireItem(admin(contextA), "tenant-a", "WAIT_CONFIRM");
        admin(contextA).updateItem(tenantItem.getId(),
                new UpdateItemCommand("Wait Confirm Updated", tenantItem.getDescription(), tenantItem.getSortNo()));

        assertThat(itemName(queryB, "tenant-a", "WAIT_CONFIRM")).isEqualTo("Wait Confirm Updated");
    }

    @Test
    void default_item_update_on_a_refreshes_tenant_a_and_tenant_b_caches_on_b() {
        DictQueryService queryB = query(contextB);
        assertThat(queryB.listEffectiveItems("ORDER_STATUS", "tenant-a"))
                .extracting(DictItemView::getCode)
                .contains("CREATED");
        assertThat(queryB.listEffectiveItems("ORDER_STATUS", "tenant-b"))
                .extracting(DictItemView::getCode)
                .containsExactly("CREATED");
        assertThat(itemName(queryB, "tenant-a", "CREATED")).isEqualTo("Created");
        assertThat(itemName(queryB, "tenant-b", "CREATED")).isEqualTo("Created");

        DictItemDetail defaultItem = requireItem(admin(contextA), null, "CREATED");
        admin(contextA).updateItem(defaultItem.getId(),
                new UpdateItemCommand("Created Updated", defaultItem.getDescription(), defaultItem.getSortNo()));

        assertThat(itemName(queryB, "tenant-a", "CREATED")).isEqualTo("Created Updated");
        assertThat(itemName(queryB, "tenant-b", "CREATED")).isEqualTo("Created Updated");
    }

    private static ConfigurableApplicationContext startContext() {
        return new SpringApplicationBuilder(DictExampleApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.profiles.active=redis",
                        "--spring.main.web-application-type=none",
                        "--spring.datasource.url=" + ExampleMysqlSupport.MYSQL.getJdbcUrl(),
                        "--spring.datasource.username=" + ExampleMysqlSupport.MYSQL.getUsername(),
                        "--spring.datasource.password=" + ExampleMysqlSupport.MYSQL.getPassword(),
                        "--spring.datasource.driver-class-name=com.mysql.jdbc.Driver",
                        "--vincent.dict.admin.enabled=true",
                        "--spring.redis.host=" + REDIS.getHost(),
                        "--spring.redis.port=" + REDIS.getMappedPort(6379));
    }

    private static void restoreDemoItemNames(DictAdminService admin) {
        DictItemDetail tenantItem = requireItem(admin, "tenant-a", "WAIT_CONFIRM");
        admin.updateItem(tenantItem.getId(),
                new UpdateItemCommand("Wait Confirm", "Tenant wait-confirm status", tenantItem.getSortNo()));
        DictItemDetail defaultItem = requireItem(admin, null, "CREATED");
        admin.updateItem(defaultItem.getId(),
                new UpdateItemCommand("Created", "Default created status", defaultItem.getSortNo()));
    }

    private static DictItemDetail requireItem(DictAdminService admin, String tenantId, String code) {
        long dictId = admin.pageDicts(new DictPageQuery("ORDER_STATUS", null, null, false, 1, 20))
                .getItems()
                .get(0)
                .getId();
        return admin.pageItems(dictId, new ItemPageQuery(tenantId, code, null, null, false, 1, 20))
                .getItems()
                .get(0);
    }

    private static String itemName(DictQueryService query, String tenantId, String code) {
        return query.findEffectiveItem("ORDER_STATUS", code, tenantId)
                .orElseThrow(() -> new AssertionError("missing effective item " + code + " for " + tenantId))
                .getName();
    }

    private static DictQueryService query(ConfigurableApplicationContext context) {
        return context.getBean(DictQueryService.class);
    }

    private static DictAdminService admin(ConfigurableApplicationContext context) {
        return context.getBean(DictAdminService.class);
    }

    private static void closeQuietly(ConfigurableApplicationContext context) {
        if (context != null) {
            context.close();
        }
    }
}
