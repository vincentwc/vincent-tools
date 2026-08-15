# vincent-dict MySQL Schema

手工维护的 MySQL 5.7 Schema。组件不使用 Flyway，运行时也不执行 DDL。

## 1.0.0

- 脚本：`1.0.0/001-init.sql`
- Schema 版本：`1`（写入 `vin_dict_meta.id = 1`）
- 适用：MySQL 5.7.44+，InnoDB，`utf8mb4`

在空库上执行一次：

```bash
mysql --default-character-set=utf8mb4 -u <user> -p <database> < 1.0.0/001-init.sql
```

注意：

- MySQL DDL 可能隐式提交，不要把本脚本放进业务事务。
- 不要使用 `CREATE TABLE IF NOT EXISTS` 掩盖结构不一致。
- 脚本不含业务种子数据。
