# ADR 0006 — 业务库持久化采用 MyBatis-Plus（保留领域分包）

- 状态：Accepted
- 日期：2026-10-25

## 背景（Context）

Stage-1 早期用 Spring `JdbcTemplate` + 手写 SQL 实现仓储，便于控制钱包冻结与 job 状态机的条件更新。
表与用例增长后，继续以「禁止 ORM、全盘手写 SQL」为文档目标会推高维护成本，也不符合团队对可扩展量级的预期。

同时必须避免另一极端：借换 ORM 之机把代码改成全局 `controller` / `service` / `mapper`，抹掉 `auth` / `billing` / `job` 等业务边界。

## 决策（Decision）

- flora `api`（及需要访问同一业务库的 JVM 模块）的**目标持久化框架**为 **MyBatis-Plus**（基于 MyBatis）。
- `JdbcTemplate` / 手写 `Jdbc*Repository` 视为**过渡实现**，须通过后续实现 WP 迁到 Mapper；新写的文档与新代码不得再把「不引入 ORM」写成冻结栈。
- **包结构不变**：按领域分包 `com.flora.api.<bounded-context>.{web,application,domain,infra}`。
  - `web` ≈ Interface（HTTP）
  - `application` = 用例
  - `domain` = 状态机与不变量
  - `infra` = MyBatis-Plus Mapper 与适配；可保留 Repository 接口由 application/domain 依赖
- **禁止**改为仓库根下全局的 `controller` / `service` / `mapper` 三目录作为主布局。
- 跨领域调用只通过对方的 **application 用例**（或明确的领域服务端口），禁止 `job` 直接依赖 `billing` 的 Mapper / 表。
- 下列路径**必须**继续使用显式 SQL（注解 `@Update` / `@Select` 或 XML），不得改成「加载实体 → 改字段 → 全量 update」而导致丢失 `WHERE status=?` 条件：
  - 钱包：`freeze` / `confirm` / `release`（含 `wallet` 与 `wallet_hold` 条件更新）
  - Job：带前置状态的乐观迁移（如 `queued → running`）
- 与 ADR 0002 的关系：运行时仍是 Java 21 + Spring Boot 3 + PostgreSQL；本 ADR 只冻结 ORM 选择与包内落点。

## 后果（Consequences）

- 正向：CRUD 与条件构造可随表增长扩展；文档与实现目标一致。
- 正向：领域边界与四层职责保留，换 ORM 不换架构。
- 代价：需一次迁移 WP 引入依赖、改 `infra`、对齐单测；迁移完成前仓内仍可能并存 JdbcTemplate（以本 ADR 为准，不以旧注释为准）。
- 代价：新人需理解「领域分包内的 MVC 角色」，而不是按全局目录找 Controller。
- 不做：本决策不授权引入第二套用户额度账本，不授权 worker 直接写钱包（仍守 ADR 0001 / 0004）。
