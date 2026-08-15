# vincent-tools

可持续扩展的通用 Java 工具仓库。当前首个工具是 `vincent-dict`；Phase 0 已抽取共享模块 `vincent-common`（`vincent-host-ports`、`vincent-common-core`、`vincent-common-web`、`vincent-common-cache-redis`），供 dict 等嵌入式组件复用。

## 三种制品，不要混用

| 制品 | 坐标 | 用途 |
| --- | --- | --- |
| 根父 POM | `com.vincent.tools:vincent-tools` | 本仓库的内部父工程与源码聚合。只给本仓库构建使用。 |
| BOM | `com.vincent.tools:vincent-tools-bom` | 业务系统在 `dependencyManagement` 中 `import`，对齐已发布 Vincent 制品版本。 |
| 功能 Starter | 例如 `com.vincent.tools:vincent-dict-boot2-starter` | 业务系统真正引入的功能依赖。 |

业务系统不要把根父 POM 当作功能依赖，也不要继承它来接入某个工具。接入方式是：导入 BOM，再按需添加对应功能 Starter。

## Vincent Dict

字典查询组件。消费者说明见 [vincent-dict/README.md](vincent-dict/README.md)。

兼容性基线：Java 8、Spring Boot `2.2.6.RELEASE`、MyBatis-Plus `3.3.2`、MySQL 5.7+。核心查询不依赖 Redis；跨实例缓存是按需启用的额外 Starter，需要宿主提供 `StringRedisTemplate`，默认 TTL 60 秒。Redis 健康时写入后立即可见；Redis 不可用时回退 MySQL，第一版不提供强一致性。
