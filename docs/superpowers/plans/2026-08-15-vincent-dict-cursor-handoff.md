# Vincent Dict Cursor 续执行交接

## 1. 当前恢复点

- 工作树：`/Users/vincent/developEnv/code/vincent-tools/.worktrees/vincent-dict`
- 分支：`feature/vincent-dict`
- Task 4 实现提交：`b089b8c feat(dict): add query application service`
- 当前 HEAD 应为本交接文档的最新提交；请以 `git rev-parse --short HEAD` 的实际输出为准。
- 主工作目录仍在 `main`；其中用户自己的 `.gitignore` 修改没有纳入功能分支。
- 所有 Codex 子代理已停止，不会继续后台修改文件。

请在 Cursor 中直接打开上述 worktree，不要打开外层 main 工作目录继续编码。

## 2. 文档入口

- 设计：`docs/superpowers/specs/2026-08-14-vincent-dict-design.md`
- 核心计划：`docs/superpowers/plans/2026-08-14-vincent-dict-core.md`
- 管理端计划：`docs/superpowers/plans/2026-08-14-vincent-dict-admin.md`
- Redis 计划：`docs/superpowers/plans/2026-08-14-vincent-dict-redis.md`
- 本交接：`docs/superpowers/plans/2026-08-15-vincent-dict-cursor-handoff.md`
- 本地 SDD 台账：`.superpowers/sdd/2026-08-14-vincent-dict-core/progress.md`
- Task 4 实施报告：`.superpowers/sdd/2026-08-14-vincent-dict-core/task-4-report.md`

`.superpowers/` 已被 Git 忽略，但当前 worktree 中的台账、简报和报告均已保留。

## 3. 已完成进度

| 任务 | 状态 | 提交范围 |
| --- | --- | --- |
| Core Task 1：Maven/BOM/模块骨架 | 已完成并审查 | `6d3b194`、`eb538bb` |
| Core Task 2：领域值对象 | 已完成并审查 | `e746780`、`1484175` |
| Core Task 3：领域聚合与策略 | 已完成并审查 | `1885b06`、`236528f` |
| Core Task 4：查询应用 API | 实现已提交，审查未完成 | `b089b8c` |
| Core Task 5-7 | 未开始 | — |
| Admin Task 1-6 | 未开始 | — |
| Redis Task 1-3 | 未开始 | — |

Task 4 已完成 TDD 和验证：domain 60 项、application 21 项，共 81 项测试通过；application compile 依赖只有 domain。该结果记录在 Task 4 报告中，但独立规格/质量审查在切换工具时被中断，所以 Task 4 不能直接标记完成。

## 4. Cursor 中的第一步

在 Cursor 终端执行：

```bash
cd /Users/vincent/developEnv/code/vincent-tools/.worktrees/vincent-dict
git branch --show-current
git status --short
git log --oneline -10
```

预期分支为 `feature/vincent-dict`，工作树应保持干净；`git log` 顶部应为交接文档提交，其后可看到 Task 4 的 `b089b8c`。

本机 Maven settings 默认启用了与 Java 8 冲突的 `jdk-17` profile。所有项目验证都需要显式禁用它：

```bash
mvn -P '!jdk-17' test
```

不要修改用户级 `~/.m2/settings.xml`。

## 5. 先完成 Task 4 审查

在 Cursor Agent 中粘贴：

```text
请只审查 Core Task 4，不要继续实现 Task 5。

先阅读：
1. docs/superpowers/plans/2026-08-14-vincent-dict-core.md 中的 Task 4
2. .superpowers/sdd/2026-08-14-vincent-dict-core/task-4-report.md

审查范围严格限定为 git diff 236528f..b089b8c。
先检查规格符合性，再检查代码质量和测试质量。重点核对：
- DictQueryService 四个重载；
- 显式 tenantId 必须拒绝 "0"，只有 SingleTenantProvider 内部路径允许 "0" 哨兵；
- missing dict 抛 DICT_NOT_FOUND，disabled dict 返回空；
- sortNo/code/persisted id 排序；
- 2000/2001 上限；
- final immutable view、不可修改且防御复制的列表；
- NoopDictCache loader 恰好调用一次并原样传播数据库异常；
- find 必须复用 list 语义；
- application 编译依赖只能是 domain 和 JDK。

不要修改代码。每个问题给出 file:line，按 Critical/Important/Minor 分类，并给出 Spec verdict 与 Quality verdict。
```

若审查无 Critical/Important 且规格通过，在台账追加：

```text
Task 4: complete (commits 236528f..b089b8c, review clean)
```

若审查有问题：先补失败测试，再修实现；运行聚焦测试和 `mvn -P '!jdk-17' test`，提交修复后仅复审修复 diff，最后再更新台账。

## 6. 后续执行顺序

严格按以下顺序继续，不并行修改同一工作树：

1. Core Task 5：手工 MySQL SQL、PO 映射、查询仓储；
2. Core Task 6：Boot 2 自动配置和 Schema 快速失败校验；
3. Core Task 7：兼容性示例、消费者文档和核心验收；
4. 核心计划全分支审查；
5. Admin Task 1-6；
6. Admin 全分支审查；
7. Redis Task 1-3；
8. 最终全工程测试、审查和集成。

每个 Task 都执行：测试先行（RED）→ 最小实现（GREEN）→ 完整验证 → 独立提交 → 规格/质量审查 → 必要修复与复审。一次只处理一个 Task。

## 7. 已知待最终处理项

- Task 1：功能分支 `.gitignore` 删除了已有 `.worktrees/` 规则，最终审查应恢复。
- Task 2：`TenantId.of("0")` 的早期测试只断言异常类型，未直接断言稳定错误码。
- Task 3：可进一步直接断言创建时 `version=0`、完整维护元数据和成功变更后的维护元数据。
- 聚合 package-private rebuild 跨 MyBatis 模块访问尚未实现。后续若确需跨包调用，优先增加受控 domain factory/bridge，不要把聚合内部重建方法直接公开。

这些是已登记的 Minor/后续设计点，不应跳过当前 Task 的 Critical/Important 问题。

## 8. 禁止事项

- 不要在 `main` 上直接继续功能实现；
- 不要修改兄弟业务系统；
- 不要引入 Flyway，Schema 只提供手工 SQL；
- 第一版不要增加审计表/操作审计；
- 不要让核心 Starter 强制非 Web 宿主变成 Web 应用；
- 不要把租户默认值 `"0"` 当作普通外部 tenantId；
- 不要绕过统一查询服务直接拼接默认项和租户项。
