package com.vincent.tools.dict.infra.mybatis;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.vincent.tools.dict.application.admin.PageResult;
import com.vincent.tools.dict.application.admin.query.DictPageQuery;
import com.vincent.tools.dict.application.admin.query.ItemPageQuery;
import com.vincent.tools.dict.application.admin.view.DictItemDetail;
import com.vincent.tools.dict.application.admin.view.DictSummary;
import com.vincent.tools.dict.domain.Dict;
import com.vincent.tools.dict.domain.DictCode;
import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;
import com.vincent.tools.dict.domain.DictItem;
import com.vincent.tools.dict.domain.DictItemPolicy;
import com.vincent.tools.dict.domain.DictItemSource;
import com.vincent.tools.dict.domain.DictStatus;
import com.vincent.tools.dict.domain.ItemCode;
import com.vincent.tools.dict.domain.ItemCodeUsage;
import com.vincent.tools.dict.domain.ItemStatus;
import com.vincent.tools.dict.domain.TenantId;
import com.vincent.tools.dict.infra.mybatis.mapper.DictItemMapper;
import com.vincent.tools.dict.infra.mybatis.mapper.DictMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MybatisDictAdminRepositoryIT {
    private static final Instant NOW = Instant.parse("2026-08-14T12:34:56.789Z");
    private static final String OPERATOR = "operator";
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:5.7.44"))
                    .withUrlParam("useSSL", "false")
                    .withStartupTimeout(Duration.ofMinutes(3));

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void startDatabase() throws Exception {
        MYSQL.start();
        applyInitSql();
        sqlSessionFactory = createSqlSessionFactory();
    }

    @AfterAll
    static void stopDatabase() {
        MYSQL.stop();
    }

    @BeforeEach
    void cleanBusinessTables() throws SQLException {
        Connection connection = openConnection();
        Statement statement = connection.createStatement();
        try {
            statement.execute("DELETE FROM vin_dict_item");
            statement.execute("DELETE FROM vin_dict");
        } finally {
            statement.close();
            connection.close();
        }
    }

    @Test
    void creates_updates_disables_deletes_and_restores_a_dictionary() {
        SqlSession session = sqlSessionFactory.openSession();
        try {
            MybatisDictAdminRepository repository = repository(session);
            Dict created = Dict.create(DictCode.of("ORDER_STATUS"), "Order status", "Lifecycle", 10, OPERATOR, NOW);

            long dictId = repository.insertDict(created);
            session.commit();

            Dict loaded = repository.findDict(dictId).get();
            assertThat(loaded.id()).isEqualTo(dictId);
            assertThat(loaded.code().value()).isEqualTo("ORDER_STATUS");
            assertThat(loaded.name()).isEqualTo("Order status");
            assertThat(loaded.description()).isEqualTo("Lifecycle");
            assertThat(loaded.status()).isEqualTo(DictStatus.ENABLED);
            assertThat(loaded.sortNo()).isEqualTo(10);
            assertThat(loaded.version()).isEqualTo(0);
            assertThat(loaded.isDeleted()).isFalse();
            assertThat(loaded.createdBy()).isEqualTo(OPERATOR);
            assertThat(loaded.createdAt()).isEqualTo(NOW);
            assertThat(repository.existsDictCode(DictCode.of("ORDER_STATUS"))).isTrue();
            assertThat(repository.lockDict(dictId).get().code().value()).isEqualTo("ORDER_STATUS");

            loaded.update("Order lifecycle", "Updated", 30, "editor", NOW.plusSeconds(1));
            repository.updateDict(loaded);
            session.commit();

            Dict updated = repository.findDict(dictId).get();
            assertThat(updated.name()).isEqualTo("Order lifecycle");
            assertThat(updated.description()).isEqualTo("Updated");
            assertThat(updated.sortNo()).isEqualTo(30);
            assertThat(updated.code().value()).isEqualTo("ORDER_STATUS");
            assertThat(updated.version()).isEqualTo(1);
            assertThat(updated.updatedBy()).isEqualTo("editor");

            updated.disable("editor", NOW.plusSeconds(2));
            repository.updateDict(updated);
            session.commit();
            assertThat(repository.findDict(dictId).get().status()).isEqualTo(DictStatus.DISABLED);

            Dict disabled = repository.findDict(dictId).get();
            disabled.enable("editor", NOW.plusSeconds(3));
            repository.updateDict(disabled);
            session.commit();
            assertThat(repository.findDict(dictId).get().status()).isEqualTo(DictStatus.ENABLED);

            Dict present = repository.findDict(dictId).get();
            present.delete(repository.countUndeletedItems(dictId), "editor", NOW.plusSeconds(4));
            repository.updateDict(present);
            session.commit();
            assertThat(repository.findDict(dictId).get().isDeleted()).isTrue();
            assertThat(repository.existsDictCode(DictCode.of("ORDER_STATUS"))).isTrue();

            Dict deleted = repository.findDict(dictId).get();
            deleted.restore("editor", NOW.plusSeconds(5));
            repository.updateDict(deleted);
            session.commit();
            assertThat(repository.findDict(dictId).get().isDeleted()).isFalse();
        } finally {
            session.close();
        }
    }

    @Test
    void creates_updates_disables_deletes_and_restores_an_item() {
        SqlSession session = sqlSessionFactory.openSession();
        try {
            MybatisDictAdminRepository repository = repository(session);
            long dictId = repository.insertDict(Dict.create(
                    DictCode.of("ORDER_STATUS"), "Order status", "", 10, OPERATOR, NOW));
            DictItem created = DictItem.create(dictId, ItemCode.of("WAIT_CONFIRM"), "Waiting",
                    TenantId.defaultItem(), "Pending", 20, OPERATOR, NOW);

            long itemId = repository.insertItem(created);
            session.commit();

            DictItem loaded = repository.findItem(itemId).get();
            assertThat(loaded.id()).isEqualTo(itemId);
            assertThat(loaded.dictId()).isEqualTo(dictId);
            assertThat(loaded.code().value()).isEqualTo("WAIT_CONFIRM");
            assertThat(loaded.tenantId().value()).isEqualTo(TenantId.DEFAULT_VALUE);
            assertThat(loaded.name()).isEqualTo("Waiting");
            assertThat(loaded.description()).isEqualTo("Pending");
            assertThat(loaded.status()).isEqualTo(ItemStatus.ENABLED);
            assertThat(loaded.sortNo()).isEqualTo(20);
            assertThat(loaded.version()).isEqualTo(0);
            assertThat(loaded.isDeleted()).isFalse();
            assertThat(loaded.createdAt()).isEqualTo(NOW);

            loaded.update("Waiting confirm", "Updated", 25, "editor", NOW.plusSeconds(1));
            repository.updateItem(loaded);
            session.commit();

            DictItem updated = repository.findItem(itemId).get();
            assertThat(updated.name()).isEqualTo("Waiting confirm");
            assertThat(updated.description()).isEqualTo("Updated");
            assertThat(updated.sortNo()).isEqualTo(25);
            assertThat(updated.code().value()).isEqualTo("WAIT_CONFIRM");
            assertThat(updated.dictId()).isEqualTo(dictId);
            assertThat(updated.tenantId().value()).isEqualTo(TenantId.DEFAULT_VALUE);
            assertThat(updated.version()).isEqualTo(1);

            updated.disable("editor", NOW.plusSeconds(2));
            repository.updateItem(updated);
            session.commit();
            assertThat(repository.findItem(itemId).get().status()).isEqualTo(ItemStatus.DISABLED);

            DictItem disabled = repository.findItem(itemId).get();
            disabled.enable("editor", NOW.plusSeconds(3));
            repository.updateItem(disabled);
            session.commit();
            assertThat(repository.findItem(itemId).get().status()).isEqualTo(ItemStatus.ENABLED);

            DictItem present = repository.findItem(itemId).get();
            present.delete("editor", NOW.plusSeconds(4));
            repository.updateItem(present);
            session.commit();
            assertThat(repository.findItem(itemId).get().isDeleted()).isTrue();

            DictItem deleted = repository.findItem(itemId).get();
            Dict dict = repository.findDict(dictId).get();
            deleted.restore(!dict.isDeleted(), "editor", NOW.plusSeconds(5));
            repository.updateItem(deleted);
            session.commit();
            assertThat(repository.findItem(itemId).get().isDeleted()).isFalse();
        } finally {
            session.close();
        }
    }

    @Test
    void restore_item_is_rejected_when_dictionary_is_deleted() {
        SqlSession session = sqlSessionFactory.openSession();
        try {
            MybatisDictAdminRepository repository = repository(session);
            long dictId = repository.insertDict(Dict.create(
                    DictCode.of("ORDER_STATUS"), "Order status", "", 10, OPERATOR, NOW));
            long itemId = repository.insertItem(DictItem.create(
                    dictId, ItemCode.of("WAIT_CONFIRM"), "Waiting", TenantId.of("tenant-a"), "", 20, OPERATOR, NOW));
            session.commit();

            DictItem item = repository.findItem(itemId).get();
            item.delete(OPERATOR, NOW.plusSeconds(1));
            repository.updateItem(item);
            session.commit();

            Dict dict = repository.lockDict(dictId).get();
            dict.delete(repository.countUndeletedItems(dictId), OPERATOR, NOW.plusSeconds(2));
            repository.updateDict(dict);
            session.commit();

            DictItem toRestore = repository.findItem(itemId).get();
            Dict deletedDict = repository.findDict(dictId).get();
            assertThatThrownBy(() -> toRestore.restore(!deletedDict.isDeleted(), OPERATOR, NOW.plusSeconds(3)))
                    .isInstanceOf(DictException.class)
                    .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);
            assertThat(repository.findItem(itemId).get().isDeleted()).isTrue();
        } finally {
            session.close();
        }
    }

    @Test
    void deleted_item_still_occupies_the_unique_key() {
        SqlSession session = sqlSessionFactory.openSession();
        try {
            MybatisDictAdminRepository repository = repository(session);
            long dictId = repository.insertDict(Dict.create(
                    DictCode.of("ORDER_STATUS"), "Order status", "", 10, OPERATOR, NOW));
            long itemId = repository.insertItem(DictItem.create(
                    dictId, ItemCode.of("WAIT_CONFIRM"), "Waiting", TenantId.of("tenant-a"), "", 20, OPERATOR, NOW));
            session.commit();

            DictItem item = repository.findItem(itemId).get();
            item.delete(OPERATOR, NOW.plusSeconds(1));
            repository.updateItem(item);
            session.commit();

            DictItem duplicate = DictItem.create(
                    dictId, ItemCode.of("WAIT_CONFIRM"), "Again", TenantId.of("tenant-a"), "", 21, OPERATOR, NOW);
            assertThatThrownBy(() -> repository.insertItem(duplicate))
                    .isInstanceOf(PersistenceException.class);
        } finally {
            session.close();
        }
    }

    @Test
    void stale_version_update_is_optimistic_lock_conflict() {
        SqlSession session = sqlSessionFactory.openSession();
        try {
            MybatisDictAdminRepository repository = repository(session);
            long dictId = repository.insertDict(Dict.create(
                    DictCode.of("ORDER_STATUS"), "Order status", "", 10, OPERATOR, NOW));
            long itemId = repository.insertItem(DictItem.create(
                    dictId, ItemCode.of("WAIT_CONFIRM"), "Waiting", TenantId.defaultItem(), "", 20, OPERATOR, NOW));
            session.commit();

            Dict first = repository.findDict(dictId).get();
            first.update("First", "", 11, OPERATOR, NOW.plusSeconds(1));
            repository.updateDict(first);
            session.commit();

            first.update("Second", "", 12, OPERATOR, NOW.plusSeconds(2));
            assertThatThrownBy(() -> repository.updateDict(first))
                    .isInstanceOf(DictException.class)
                    .extracting("code").isEqualTo(DictErrorCode.OPTIMISTIC_LOCK_CONFLICT);

            DictItem item = repository.findItem(itemId).get();
            item.update("First", "", 21, OPERATOR, NOW.plusSeconds(1));
            repository.updateItem(item);
            session.commit();

            item.update("Second", "", 22, OPERATOR, NOW.plusSeconds(2));
            assertThatThrownBy(() -> repository.updateItem(item))
                    .isInstanceOf(DictException.class)
                    .extracting("code").isEqualTo(DictErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        } finally {
            session.close();
        }
    }

    @Test
    void pages_dicts_and_items_with_given_offset_and_filters() {
        SqlSession session = sqlSessionFactory.openSession();
        try {
            MybatisDictAdminRepository repository = repository(session);
            long firstId = insertNamedDict(repository, "ORDER_STATUS", "Order status", 10, true, false);
            insertNamedDict(repository, "ORDER_TYPE", "Order type", 20, true, false);
            insertNamedDict(repository, "PAY_STATUS", "Pay status", 30, false, false);
            long deletedId = insertNamedDict(repository, "OLD_STATUS", "Old status", 40, true, true);
            session.commit();

            PageResult<DictSummary> page = repository.pageDicts(new DictPageQuery(null, null, null, false, 2, 2));
            assertThat(page.getPage()).isEqualTo(2);
            assertThat(page.getSize()).isEqualTo(2);
            assertThat(page.getTotal()).isEqualTo(3);
            assertThat(page.getItems()).hasSize(1);
            assertThat(page.getItems().get(0).getCode()).isEqualTo("PAY_STATUS");
            assertThat(page.getItems().get(0).isEnabled()).isFalse();

            PageResult<DictSummary> byCode = repository.pageDicts(
                    new DictPageQuery("ORDER_STATUS", null, null, false, 1, 20));
            assertThat(byCode.getItems()).extracting("code").containsExactly("ORDER_STATUS");

            PageResult<DictSummary> enabled = repository.pageDicts(
                    new DictPageQuery(null, null, Boolean.TRUE, false, 1, 20));
            assertThat(enabled.getItems()).extracting("code").containsExactly("ORDER_STATUS", "ORDER_TYPE");

            PageResult<DictSummary> withDeleted = repository.pageDicts(
                    new DictPageQuery(null, null, null, true, 1, 20));
            assertThat(withDeleted.getTotal()).isEqualTo(4);
            assertThat(withDeleted.getItems()).extracting("id").contains(deletedId);

            long defaultA = repository.insertItem(DictItem.create(
                    firstId, ItemCode.of("CREATED"), "Created", TenantId.defaultItem(), "", 1, OPERATOR, NOW));
            repository.insertItem(DictItem.create(
                    firstId, ItemCode.of("PAID"), "Paid", TenantId.defaultItem(), "", 2, OPERATOR, NOW));
            long tenantItem = repository.insertItem(DictItem.create(
                    firstId, ItemCode.of("WAIT_CONFIRM"), "Waiting", TenantId.of("tenant-a"), "", 3, OPERATOR, NOW));
            long deletedItem = repository.insertItem(DictItem.create(
                    firstId, ItemCode.of("GONE"), "Gone", TenantId.defaultItem(), "", 4, OPERATOR, NOW));
            session.commit();
            DictItem gone = repository.findItem(deletedItem).get();
            gone.delete(OPERATOR, NOW.plusSeconds(1));
            repository.updateItem(gone);
            session.commit();

            PageResult<DictItemDetail> defaultPage = repository.pageItems(
                    firstId, new ItemPageQuery(null, null, null, null, false, 2, 1));
            assertThat(defaultPage.getPage()).isEqualTo(2);
            assertThat(defaultPage.getSize()).isEqualTo(1);
            assertThat(defaultPage.getTotal()).isEqualTo(2);
            assertThat(defaultPage.getItems()).hasSize(1);
            assertThat(defaultPage.getItems().get(0).getCode()).isEqualTo("PAID");
            assertThat(defaultPage.getItems().get(0).getSource()).isEqualTo(DictItemSource.DEFAULT);
            assertThat(defaultPage.getItems().get(0).getId()).isNotEqualTo(defaultA);

            PageResult<DictItemDetail> tenantPage = repository.pageItems(
                    firstId, new ItemPageQuery("tenant-a", null, null, null, false, 1, 20));
            assertThat(tenantPage.getItems()).extracting("id").containsExactly(tenantItem);
            assertThat(tenantPage.getItems().get(0).getSource()).isEqualTo(DictItemSource.TENANT);

            PageResult<DictItemDetail> includingDeleted = repository.pageItems(
                    firstId, new ItemPageQuery(null, null, null, null, true, 1, 20));
            assertThat(includingDeleted.getItems()).extracting("code").contains("GONE");
        } finally {
            session.close();
        }
    }

    @Test
    void item_code_usage_includes_deleted_rows() {
        SqlSession session = sqlSessionFactory.openSession();
        try {
            MybatisDictAdminRepository repository = repository(session);
            DictItemPolicy policy = new DictItemPolicy();
            long dictId = repository.insertDict(Dict.create(
                    DictCode.of("ORDER_STATUS"), "Order status", "", 10, OPERATOR, NOW));
            long itemId = repository.insertItem(DictItem.create(
                    dictId, ItemCode.of("WAIT_CONFIRM"), "Waiting", TenantId.defaultItem(), "", 20, OPERATOR, NOW));
            session.commit();

            DictItem item = repository.findItem(itemId).get();
            item.delete(OPERATOR, NOW.plusSeconds(1));
            repository.updateItem(item);
            session.commit();

            ItemCodeUsage usage = repository.findItemCodeUsage(
                    dictId, ItemCode.of("WAIT_CONFIRM"), TenantId.of("tenant-a"));
            assertThatThrownBy(() -> policy.checkCreate(TenantId.of("tenant-a"), usage, 0, 1000))
                    .isInstanceOf(DictException.class)
                    .extracting("code").isEqualTo(DictErrorCode.DICT_ITEM_CODE_CONFLICT);

            long otherId = repository.insertItem(DictItem.create(
                    dictId, ItemCode.of("PAID"), "Paid", TenantId.of("tenant-a"), "", 21, OPERATOR, NOW));
            session.commit();
            assertThat(otherId).isPositive();
            ItemCodeUsage otherTenant = repository.findItemCodeUsage(
                    dictId, ItemCode.of("PAID"), TenantId.of("tenant-b"));
            assertThatCode(() -> policy.checkCreate(TenantId.of("tenant-b"), otherTenant, 0, 1000))
                    .doesNotThrowAnyException();
        } finally {
            session.close();
        }
    }

    @Test
    void counts_undeleted_items_by_dict_and_tenant() {
        SqlSession session = sqlSessionFactory.openSession();
        try {
            MybatisDictAdminRepository repository = repository(session);
            long dictId = repository.insertDict(Dict.create(
                    DictCode.of("ORDER_STATUS"), "Order status", "", 10, OPERATOR, NOW));
            long defaultId = repository.insertItem(DictItem.create(
                    dictId, ItemCode.of("CREATED"), "Created", TenantId.defaultItem(), "", 1, OPERATOR, NOW));
            repository.insertItem(DictItem.create(
                    dictId, ItemCode.of("PAID"), "Paid", TenantId.of("tenant-a"), "", 2, OPERATOR, NOW));
            session.commit();

            DictItem defaultItem = repository.findItem(defaultId).get();
            defaultItem.delete(OPERATOR, NOW.plusSeconds(1));
            repository.updateItem(defaultItem);
            session.commit();

            assertThat(repository.countUndeletedItems(dictId)).isEqualTo(1);
            assertThat(repository.countUndeletedItems(dictId, TenantId.defaultItem())).isEqualTo(0);
            assertThat(repository.countUndeletedItems(dictId, TenantId.of("tenant-a"))).isEqualTo(1);
        } finally {
            session.close();
        }
    }

    @Test
    void persists_status_and_deleted_as_tinyint_zero_or_one() throws SQLException {
        SqlSession session = sqlSessionFactory.openSession();
        long dictId;
        long itemId;
        try {
            MybatisDictAdminRepository repository = repository(session);
            dictId = repository.insertDict(Dict.create(
                    DictCode.of("ORDER_STATUS"), "Order status", "", 10, OPERATOR, NOW));
            itemId = repository.insertItem(DictItem.create(
                    dictId, ItemCode.of("WAIT_CONFIRM"), "Waiting", TenantId.defaultItem(), "", 20, OPERATOR, NOW));
            session.commit();

            Dict dict = repository.findDict(dictId).get();
            dict.disable(OPERATOR, NOW.plusSeconds(1));
            repository.updateDict(dict);
            DictItem item = repository.findItem(itemId).get();
            item.delete(OPERATOR, NOW.plusSeconds(1));
            repository.updateItem(item);
            session.commit();
        } finally {
            session.close();
        }

        Connection connection = openConnection();
        try {
            assertTinyint(connection, "SELECT status, deleted FROM vin_dict WHERE id = " + dictId, 0, 0);
            assertTinyint(connection, "SELECT status, deleted FROM vin_dict_item WHERE id = " + itemId, 1, 1);
        } finally {
            connection.close();
        }
    }

    @Test
    void writes_utc_instants_into_datetime_columns() throws SQLException {
        SqlSession session = sqlSessionFactory.openSession();
        long dictId;
        try {
            dictId = repository(session).insertDict(Dict.create(
                    DictCode.of("ORDER_STATUS"), "Order status", "", 10, OPERATOR, NOW));
            session.commit();
        } finally {
            session.close();
        }

        Connection connection = openConnection();
        PreparedStatement statement = connection.prepareStatement(
                "SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f') AS created_at FROM vin_dict WHERE id = ?");
        try {
            statement.setLong(1, dictId);
            ResultSet resultSet = statement.executeQuery();
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("created_at")).startsWith("2026-08-14 12:34:56.789");
        } finally {
            statement.close();
            connection.close();
        }
    }

    @Test
    void missing_rows_are_empty_and_update_sql_omits_immutable_columns() throws Exception {
        SqlSession session = sqlSessionFactory.openSession();
        try {
            MybatisDictAdminRepository repository = repository(session);
            assertThat(repository.findDict(99L)).isEmpty();
            assertThat(repository.lockDict(99L)).isEmpty();
            assertThat(repository.findItem(99L)).isEmpty();
            assertThat(repository.existsDictCode(DictCode.of("MISSING"))).isFalse();
        } finally {
            session.close();
        }

        String dictUpdate = updateBlocks(readClasspathFile("com/vincent/tools/dict/infra/mybatis/mapper/DictMapper.xml"));
        String itemUpdate = updateBlocks(readClasspathFile("com/vincent/tools/dict/infra/mybatis/mapper/DictItemMapper.xml"));
        assertThat(dictUpdate).contains("version = #{expectedVersion}");
        assertThat(dictUpdate).contains("version + 1");
        assertThat(dictUpdate).doesNotContain("code =");
        assertThat(itemUpdate).contains("version = #{expectedVersion}");
        assertThat(itemUpdate).contains("version + 1");
        assertThat(itemUpdate).doesNotContain("code =");
        assertThat(itemUpdate).doesNotContain("dict_id =");
        assertThat(itemUpdate).doesNotContain("tenant_id =");

        String dictXml = readClasspathFile("com/vincent/tools/dict/infra/mybatis/mapper/DictMapper.xml");
        String itemXml = readClasspathFile("com/vincent/tools/dict/infra/mybatis/mapper/DictItemMapper.xml");
        assertThat(dictXml).contains("FOR UPDATE");
        assertThat(itemXml).contains("AND deleted = 0");
        assertThat(itemXml).contains("SELECT tenant_id, deleted FROM vin_dict_item");
    }

    private static long insertNamedDict(MybatisDictAdminRepository repository, String code, String name, int sortNo,
                                       boolean enabled, boolean deleted) {
        Dict dict = Dict.create(DictCode.of(code), name, "", sortNo, OPERATOR, NOW);
        if (!enabled) {
            dict.disable(OPERATOR, NOW);
        }
        long id = repository.insertDict(dict);
        if (deleted) {
            Dict stored = repository.findDict(id).get();
            stored.delete(0, OPERATOR, NOW);
            repository.updateDict(stored);
        }
        return id;
    }

    private static void assertTinyint(Connection connection, String sql, int status, int deleted) throws SQLException {
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sql);
        try {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt("status")).isEqualTo(status);
            assertThat(resultSet.getInt("deleted")).isEqualTo(deleted);
            assertThat(resultSet.getString("status")).isNotEqualTo("ENABLED");
            assertThat(resultSet.getString("status")).isNotEqualTo("DISABLED");
        } finally {
            resultSet.close();
            statement.close();
        }
    }

    private static String updateBlocks(String xml) {
        StringBuilder blocks = new StringBuilder();
        int from = 0;
        while (true) {
            int start = xml.indexOf("<update", from);
            if (start < 0) {
                break;
            }
            int end = xml.indexOf("</update>", start);
            blocks.append(xml.substring(start, end + "</update>".length()));
            from = end + 1;
        }
        return blocks.toString();
    }

    private static MybatisDictAdminRepository repository(SqlSession session) {
        return new MybatisDictAdminRepository(
                session.getMapper(DictMapper.class),
                session.getMapper(DictItemMapper.class));
    }

    private static SqlSessionFactory createSqlSessionFactory() {
        DataSource dataSource = new UnpooledDataSource(
                "com.mysql.jdbc.Driver",
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword());
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(DictMapper.class);
        configuration.addMapper(DictItemMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private static void applyInitSql() throws Exception {
        byte[] bytes = Files.readAllBytes(initSqlFile().toPath());
        String script = new String(bytes, StandardCharsets.UTF_8);
        Connection connection = openConnection();
        Statement statement = connection.createStatement();
        try {
            String[] parts = script.split(";");
            for (int index = 0; index < parts.length; index++) {
                String sql = stripSqlComments(parts[index]);
                if (!sql.isEmpty()) {
                    statement.execute(sql);
                }
            }
        } finally {
            statement.close();
            connection.close();
        }
    }

    private static String stripSqlComments(String fragment) {
        String[] lines = fragment.split("\n");
        StringBuilder sql = new StringBuilder();
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex].trim();
            if (!line.isEmpty() && !line.startsWith("--")) {
                if (sql.length() > 0) {
                    sql.append(' ');
                }
                sql.append(line);
            }
        }
        return sql.toString();
    }

    private static File initSqlFile() {
        File[] candidates = new File[] {
                new File("vincent-dict/sql/mysql/1.0.0/001-init.sql"),
                new File("../sql/mysql/1.0.0/001-init.sql"),
                new File("sql/mysql/1.0.0/001-init.sql")
        };
        for (int index = 0; index < candidates.length; index++) {
            if (candidates[index].isFile()) {
                return candidates[index];
            }
        }
        throw new IllegalStateException("missing schema script 001-init.sql");
    }

    private static String readClasspathFile(String classpath) throws Exception {
        File[] candidates = new File[] {
                new File("vincent-dict/vincent-dict-infra-mybatis/src/main/resources/" + classpath),
                new File("src/main/resources/" + classpath)
        };
        for (int index = 0; index < candidates.length; index++) {
            if (candidates[index].isFile()) {
                byte[] bytes = Files.readAllBytes(candidates[index].toPath());
                return new String(bytes, StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("missing mapper " + classpath);
    }

    private static Connection openConnection() throws SQLException {
        return MYSQL.createConnection("");
    }
}
