# vincent-tools

可持续扩展的通用 Java 工具仓库。当前首个工具是 `vincent-dict`。

## 三种制品，不要混用

| 制品 | 坐标 | 用途 |
| --- | --- | --- |
| 根父 POM | `com.vincent.tools:vincent-tools` | 本仓库的内部父工程与源码聚合。只给本仓库构建使用。 |
| BOM | `com.vincent.tools:vincent-tools-bom` | 业务系统在 `dependencyManagement` 中 `import`，对齐已发布 Vincent 制品版本。 |
| 功能 Starter | 例如 `com.vincent.tools:vincent-dict-boot2-starter` | 业务系统真正引入的功能依赖。 |

业务系统不要把根父 POM 当作功能依赖，也不要继承它来接入某个工具。接入方式是：导入 BOM，再按需添加对应功能 Starter。

## Vincent Dict

字典查询组件。消费者说明见 [vincent-dict/README.md](vincent-dict/README.md)。

兼容性基线：Java 8、Spring Boot `2.2.6.RELEASE`、MyBatis-Plus `3.3.2`、MySQL 5.7+。
