package com.vincent.tools.dict.infra.mybatis;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.vincent.tools.dict.application.DictLimits;
import com.vincent.tools.dict.application.admin.DefaultDictAdminService;
import com.vincent.tools.dict.application.admin.DictAdminPermission;
import com.vincent.tools.dict.application.admin.DictAdminService;
import com.vincent.tools.dict.application.admin.OperatorProvider;
import com.vincent.tools.dict.application.admin.PageResult;
import com.vincent.tools.dict.application.admin.PermissionProvider;
import com.vincent.tools.dict.application.admin.TenantDirectory;
import com.vincent.tools.dict.application.admin.TenantOption;
import com.vincent.tools.dict.application.admin.command.CreateDictCommand;
import com.vincent.tools.dict.application.admin.command.CreateItemCommand;
import com.vincent.tools.dict.application.port.NoopDictCache;
import com.vincent.tools.dict.domain.DictErrorCode;
import com.vincent.tools.dict.domain.DictException;
import com.vincent.tools.dict.infra.mybatis.mapper.DictItemMapper;
import com.vincent.tools.dict.infra.mybatis.mapper.DictMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConcurrentItemCreateIT {
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:5.7.44"))
                    .withUrlParam("useSSL", "false")
                    .withStartupTimeout(Duration.ofMinutes(3));

    private static DataSource dataSource;
    private static DictAdminService service;
    private static SpringTxRunner txRunner;

    @BeforeAll
    static void startDatabase() throws Exception {
        MYSQL.start();
        applyInitSql();
        dataSource = new UnpooledDataSource(
                "com.mysql.jdbc.Driver",
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword());
        SqlSessionFactory sqlSessionFactory = createSqlSessionFactory(dataSource);
        SqlSessionTemplate template = new SqlSessionTemplate(sqlSessionFactory);
        MybatisDictAdminRepository repository = new MybatisDictAdminRepository(
                template.getMapper(DictMapper.class),
                template.getMapper(DictItemMapper.class));
        TransactionTemplate transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        txRunner = new SpringTxRunner(transactionTemplate);
        service = new DefaultDictAdminService(
                repository,
                txRunner,
                new NoopDictCache(),
                new FixedOperatorProvider(),
                new AllowAllPermissions(),
                new AllowAllTenants(),
                DictLimits.defaults(),
                Clock.system(ZoneOffset.UTC));
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
    void concurrent_default_and_tenant_wait_confirm_has_one_winner() throws Exception {
        long dictId = service.createDict(new CreateDictCommand("ORDER_STATUS", "Order status", "", 10));
        CreateItemCommand command = new CreateItemCommand("WAIT_CONFIRM", "Waiting", "", 20);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger();
        AtomicReference<Throwable> defaultFailure = new AtomicReference<Throwable>();
        AtomicReference<Throwable> tenantFailure = new AtomicReference<Throwable>();

        Thread defaultThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    start.await();
                    service.createDefaultItem(dictId, command);
                    successes.incrementAndGet();
                } catch (Throwable failure) {
                    defaultFailure.set(failure);
                } finally {
                    done.countDown();
                }
            }
        }, "default-item-create");
        Thread tenantThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    start.await();
                    service.createTenantItem(dictId, "tenant-a", command);
                    successes.incrementAndGet();
                } catch (Throwable failure) {
                    tenantFailure.set(failure);
                } finally {
                    done.countDown();
                }
            }
        }, "tenant-item-create");

        defaultThread.start();
        tenantThread.start();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS))
                .as("concurrent creates must finish without deadlock")
                .isTrue();

        List<Throwable> failures = new ArrayList<Throwable>();
        if (defaultFailure.get() != null) {
            failures.add(defaultFailure.get());
        }
        if (tenantFailure.get() != null) {
            failures.add(tenantFailure.get());
        }
        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOf(DictException.class);
        assertThat(((DictException) failures.get(0)).getCode()).isEqualTo(DictErrorCode.DICT_ITEM_CODE_CONFLICT);
        assertThat(countItems(dictId)).isEqualTo(1);
    }

    @Test
    void required_rolls_back_on_runtime_exception() throws Exception {
        long dictId = service.createDict(new CreateDictCommand("ORDER_TYPE", "Order type", "", 10));

        assertThatThrownBy(() -> txRunner.required(() -> {
            service.createDefaultItem(dictId, new CreateItemCommand("CREATED", "Created", "", 1));
            throw new DictException(DictErrorCode.INVALID_ARGUMENT, "force rollback");
        })).isInstanceOf(DictException.class)
                .extracting("code").isEqualTo(DictErrorCode.INVALID_ARGUMENT);

        assertThat(countItems(dictId)).isEqualTo(0);
    }

    private static int countItems(long dictId) throws SQLException {
        Connection connection = openConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM vin_dict_item WHERE dict_id = " + dictId);
        try {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        } finally {
            resultSet.close();
            statement.close();
            connection.close();
        }
    }

    private static SqlSessionFactory createSqlSessionFactory(DataSource dataSource) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test", new SpringManagedTransactionFactory(), dataSource));
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

    private static Connection openConnection() throws SQLException {
        return MYSQL.createConnection("");
    }

    private static final class FixedOperatorProvider implements OperatorProvider {
        @Override
        public String currentOperatorId() {
            return "operator";
        }
    }

    private static final class AllowAllPermissions implements PermissionProvider {
        @Override
        public boolean hasPermission(DictAdminPermission permission, Optional<String> targetTenantId) {
            return true;
        }
    }

    private static final class AllowAllTenants implements TenantDirectory {
        @Override
        public PageResult<TenantOption> search(String keyword, int page, int size) {
            return new PageResult<TenantOption>(Collections.<TenantOption>emptyList(), 0, page, size);
        }

        @Override
        public boolean exists(String tenantId) {
            return true;
        }
    }
}
