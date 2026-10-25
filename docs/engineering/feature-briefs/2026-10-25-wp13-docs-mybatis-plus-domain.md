# Feature brief：WP13 工程文档对齐 — MyBatis-Plus 持久化 + 领域分包（改历史文档，非另开修订页）

- 日期：2026-10-25
- 作者：flora engineering
- 对应 WP / 里程碑：WP13 / 工程真相源对齐（docs-first；ORM 代码迁移为后续 WP）
- 状态：已确认（待实现）
- 建议分支：`feat/wp13-docs-mybatis-plus-domain`
- 预计 PR 数：1（**以文档为主**；允许顺手改 `api/pom.xml` 里「不引入 ORM」注释一行；**禁止**本 WP 全量把 `Jdbc*Repository` 换成 Mapper）

把本文件从「# Feature brief」起到文末 **整份粘贴** 给 Minimax。不要摘要。实现者只改本 brief 点名的路径；验收以「仓内工程文档与冻结栈一致」为准。

---

## 0. 给实施者（Minimax）的先读指令

动工前**必须**按顺序读完：

1. `AGENTS.md`
2. `docs/engineering/TECH_STACK.md`
3. `docs/engineering/ARCHITECTURE.md`
4. `docs/engineering/adr/0002-java-spring-business-api.md`
5. `docs/engineering/adr/README.md`
6. `docs/engineering/data-model/README.md`
7. `docs/engineering/data-model/billing.md`
8. `docs/engineering/feature-briefs/README.md`
9. `api/pom.xml`（约第 44 行注释：`用户表由 Flyway 建，JDBC 直连，不引入 ORM。`）
10. 对照真实包布局（只读，本 WP 不改 Java 业务逻辑）：
    `api/src/main/java/com/flora/api/{auth,billing,job,admin,project,i18n}/` 下已有 `web` / `application` / `domain` / `infra`

**本 WP 的硬约束：**

- **改历史文档本身**（TECH_STACK / ARCHITECTURE / ADR / data-model / feature-briefs 索引 / DEVELOPMENT 等），**不要**另建「修订说明 / changelog 页」当交付物。
- **不要**重写 WP0–WP12 历史 brief 正文去把 JdbcTemplate 改成 MyBatis-Plus（它们是实现记录）。只允许在 `feature-briefs/README.md` 顶部加一句「持久化目标以 ADR 0006 为准；旧 brief 可能仍写 JdbcTemplate」。
- **不要**新建全局 `controller/` / `service/` / `mapper/` 包，**不要**搬迁现有 Java 包。
- **不要**在本 WP 做 Flyway 新迁移去批量 DROP 已有物理外键。
- **不要**改 `docs/content/**`、不要改 `CHANGELOG.md`、不要改前端、不要改 worker 业务代码。

---

## 1. 背景

Stage-1（WP0–WP12）落地时，`api` 用 `spring-boot-starter-jdbc` + 手写 `Jdbc*Repository`，`api/pom.xml` 注释写「不引入 ORM」。这在表少、状态机 SQL 多时说得通，但**不是长期目标**：表与 CRUD 增长后，手写 SQL 会不可维护。

产品侧已拍板：

1. **长期持久化 = MyBatis-Plus**（JdbcTemplate 仅过渡）。
2. **包结构继续领域分包**：`com.flora.api.<domain>.{web,application,domain,infra}`，**不是**经典全局 MVC 三目录。
3. **工程文档必须原地改到与上述一致**，不能只写一份「修订笔记」而让 TECH_STACK / ARCHITECTURE 仍暗示「无 ORM / 仅 JDBC」。
4. 按可扩展量级设计文档口径：用户表只扛身份；余额只在钱包；充值订单与第三方支付适配属 **billing**；**前向策略**以逻辑外键 + 索引 + 写路径保证为主（已有迁移里的物理 FK 本 WP 不批量拆除）。

代码层 `Jdbc*Repository` → Mapper 的全量迁移是 **后续 WP**（本 brief「非范围 / 下一步」），本 WP 验收 = **文档真相源对齐**。

---

## 2. 目标（可验证）

1. `TECH_STACK.md` 业务 API 表明确冻结 **MyBatis-Plus** 为持久化框架；全文不再暗示「禁止 ORM / 仅 JDBC 直连」为最终态。
2. 新增 **ADR 0006**（Accepted）：MyBatis-Plus 取代 JdbcTemplate 作为目标持久化；钱包 freeze/confirm/release 与 job 乐观状态更新仍允许显式 SQL / `@Update`；**不**改为经典 MVC 包布局。
3. `ADR 0002` 决策段点名持久化框架，并指向 ADR 0006（或在 Decision 中直接写 MyBatis-Plus + 链接 0006）。
4. `ARCHITECTURE.md` 写清：**领域包 + 四层**、跨域只经 application、Infrastructure 使用 MyBatis-Plus Mapper（可保留 Repository 端口）。
5. `data-model/README.md` + `billing.md`：余额只在 `wallet`；`users` 有 `password_hash` 但无余额字段；**前向外键策略** = 逻辑关联 + 必要索引 + 写路径校验；已有物理 FK 保留、本 WP 不 drop。
6. `feature-briefs/README.md` 索引增加 WP13；顶部注明持久化目标以 ADR 0006 为准。
7. `DEVELOPMENT.md` 增加 WP13 工作包条目；依赖图可注明 WP13 为文档对齐（可与后续 ORM 代码 WP 分开）。
8. `api/pom.xml` 注释从「不引入 ORM」改为指向 MyBatis-Plus 目标（**允许仍保留** `spring-boot-starter-jdbc` 依赖至代码迁移 WP；本 WP **不强制**引入 `mybatis-plus-boot-starter`，除非实施者认为「注释与依赖声明一并改」更干净——若引入依赖，必须能编译，但**仍禁止**改 Repository 实现）。
9. 本 brief 全文落入仓：`docs/engineering/feature-briefs/2026-10-25-wp13-docs-mybatis-plus-domain.md`。

---

## 3. 范围（允许新增 / 修改）

| 路径 | 动作 |
| --- | --- |
| `docs/engineering/feature-briefs/2026-10-25-wp13-docs-mybatis-plus-domain.md` | **新增**（本文件全文入仓） |
| `docs/engineering/feature-briefs/README.md` | 顶部加持久化说明一句；索引表新增 WP13 行 |
| `docs/engineering/TECH_STACK.md` | 业务 API 表增加「持久化 / ORM」冻结行；必要时在「明确不进」澄清「禁止的是第二套账本/上帝类，不是禁止 ORM」 |
| `docs/engineering/ARCHITECTURE.md` | 扩展「分层（api）」：领域分包示意 + 跨域调用规则 + infra = MyBatis-Plus |
| `docs/engineering/adr/0002-java-spring-business-api.md` | Decision 增补 MyBatis-Plus，并链到 0006 |
| `docs/engineering/adr/0006-mybatis-plus-persistence.md` | **新增** Accepted ADR |
| `docs/engineering/adr/README.md` | 索引表增加 0006 |
| `docs/engineering/data-model/README.md` | `users` 说明「身份 only、无余额」；「通用约定」改写前向 FK 策略 |
| `docs/engineering/data-model/billing.md` | 强调余额只在 wallet；充值订单/支付适配属 billing（可写「后续表，归属本域」）；FK 口径与 README 一致 |
| `docs/engineering/DEVELOPMENT.md` | 新增 WP13 小节；可选更新工作包依赖图 |
| `docs/engineering/CONTRIBUTING.md` | PR 检查清单增加 1～2 条：遵守 ADR 0006；禁止改成全局 controller/service/mapper |
| `api/pom.xml` | **仅**修正「不引入 ORM」类注释（见下）；依赖是否引入 MP 见目标第 8 条 |

---

## 4. 非范围（本 PR 明确不做）

- **全量**把 `Jdbc*Repository` / `JdbcWallet*` / `JdbcJob*` 等改成 MyBatis-Plus Mapper（留给 WP14 或后续 ORM 迁移 brief）。
- 重写 WP0–WP12 brief 正文、重写历史迁移 SQL、批量 DROP 物理 FK。
- 把包改成全局 `controller` / `service` / `mapper`。
- 新建 `recharge_order` 表或对接微信/支付宝（只在文档里声明**归属 billing**）。
- 改 `web/`、`worker/` 业务逻辑、改公开 `docs/content/**`。
- 另开「文档修订说明」页面作为唯一交付（允许 ADR / TECH_STACK 内写 Consequences，但真相源必须是被改过的历史文件本身）。

---

## 5. 数据表字段

**无表结构变更。** 本 WP 不新增 / 不修改 Flyway 迁移。

文档口径补充（写进 data-model，不是改列）：

| 概念 | 落点 |
| --- | --- |
| 用户身份 | `users`（含 `password_hash`；**无** `available` / `balance`） |
| 余额 / 冻结 | 仅 `wallet.available` / `wallet.frozen` |
| 流水 | `wallet_ledger` |
| 冻结单 | `wallet_hold` |
| 未来充值订单 / 支付渠道适配 | **billing 域**（表名待后续 brief；本 WP 不建表） |

---

## 6. API

无 HTTP API 变更。

---

## 7. 状态机

无。钱包 / job 状态机语义不变；仅文档声明：乐观更新与 freeze/confirm/release 在 MP 落地后仍用显式 `@Update` / XML SQL，不靠脏检查隐式改余额。

---

## 8. 逐文件修改说明书（照抄执行）

### 8.1 新增 `docs/engineering/adr/0006-mybatis-plus-persistence.md`

全文采用 ADR 固定四段（中文标题）。建议正文如下（可微调措辞，但 Decision 要点不得删）：

```markdown
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
```

### 8.2 修改 `docs/engineering/adr/README.md`

在索引表末尾追加一行：

| [0006](./0006-mybatis-plus-persistence.md) | 业务库持久化采用 MyBatis-Plus（保留领域分包） | Accepted |

「何时新写一条」可补一句示例：「换 ORM / 改持久化框架」。

### 8.3 修改 `docs/engineering/adr/0002-java-spring-business-api.md`

在 **决策（Decision）** 列表中，在「持久化 PostgreSQL…」那条**之后**增加（或改写为）：

- 持久化访问框架：**MyBatis-Plus**（详见 [ADR 0006](./0006-mybatis-plus-persistence.md)）。早期 JdbcTemplate 实现视为过渡，不得再写成 stage-1 最终冻结。

在 **后果** 可加一条：实现须落在领域包的 `infra`，禁止借 Spring 习惯堆全局 `*CenterService` 或全局 mapper 包。

**不要**改 0002 的状态（仍为 Accepted）；不要把 0002 整篇 superseded，除非只在文首加一行「持久化细节以 0006 为准」。

### 8.4 修改 `docs/engineering/TECH_STACK.md`

在 **业务 API（将入仓）** 表格中增加一行（若该节标题已过时可改为「业务 API」）：

| 持久化框架 | **MyBatis-Plus**（目标）；见 [ADR 0006](./adr/0006-mybatis-plus-persistence.md)。JdbcTemplate 仅过渡，文档与新代码不得再写「禁止 ORM」 |

可选：在「明确不进 stage-1」**不要**把 MyBatis-Plus 列进去。若需澄清，加一条非目标说明：

- 不是「禁止一切 ORM」，而是禁止第二套账本、禁止上帝类、禁止 worker 持有画布/钱包确认逻辑。

计费表可补半句：余额只在 flora `wallet*`；用户表不存余额。

### 8.5 修改 `docs/engineering/ARCHITECTURE.md`

重写 / 扩展 **「分层（api）」** 一节，必须包含以下要点（可用表格或树状示意）：

1. **按领域分包**（与仓内一致）：至少点名 `auth`、`billing`、`job`、`admin`、`project`、`i18n`（及已有 `storage` / `gateway` 若适用）。
2. 每个领域包内四层：
   - `web`：HTTP / DTO（Interface）
   - `application`：用例编排
   - `domain`：状态机与不变量
   - `infra`：**MyBatis-Plus Mapper** + 消息/TOS 等适配；application 依赖仓储端口，不直接散落 SQL 在 web
3. **跨域**：只调用对方 `application`（例：`job` 提交 → `billing` 的 freeze/confirm/release），禁止直接注入他域 Mapper。
4. **与经典 MVC 的关系**：MVC 角色仍在，但是嵌在领域包内，不是全局三目录。
5. Infrastructure 描述中把「Postgres 仓储」改为「Postgres + MyBatis-Plus（ADR 0006）」；可注明迁移完成前可能仍见 Jdbc 实现。

可选增加一小节「领域与资金边界」：

- `auth` / users = 身份（含密码哈希）
- `billing` = 钱包、流水、未来充值订单与支付适配
- `job` = 任务生命周期；通过 hold_id 关联，不直接改余额

更新文首「当前仓库仍主要是 web/」等过时表述时：**若**仓内已有 api/worker，改为反映现状（有 api/worker）；不要谎称未入仓。以仓库真实状态为准做一次诚实 scrub（仅改明显过时句，不借机大重构全文）。

### 8.6 修改 `docs/engineering/data-model/README.md`

1. 在 `users` 小节标题下或表后加明确句：

   > `users` 只表示登录身份与资料（含 `password_hash`）。**禁止**在用户表存 `available` / `frozen` / 余额类字段；资金只在 [billing.md](./billing.md) 的 `wallet*`。

2. 将 **通用约定** 中：

   > `FK 指向 users.id 的列在 users 表落地前先不建物理约束，由实现 WP 决定顺序。`

   **改写为前向策略**（大意必须保留）：

   - **前向策略（WP13 起）**：新表之间的关联默认用**逻辑外键**（列 + 索引 + 应用写路径校验 / 事务），**不以**数据库 `FOREIGN KEY` 约束作为主一致性手段。
   - 金额、状态机相关写入必须在用例事务内保证合法 `user_id` / `job_id` / `hold_id`；禁止依赖 `ON DELETE CASCADE` 级联删流水。
   - **存量**：已落地迁移中的物理 FK（如 `job.hold_id → wallet_hold`、`job_step.provider_task_id → provider_task`）**保留**；本 WP **不**批量 DROP。是否在未来迁移中弱化，另开 brief。
   - 文档列上的 `FK → …` 表示逻辑关联与索引要求，不强制「下一步迁移必须加物理 CONSTRAINT」。

### 8.7 修改 `docs/engineering/data-model/billing.md`

1. 在文首或 `wallet` 节强调：

   > 用户余额**只**存在于 `wallet`（及变动流水 `wallet_ledger`）。`users` 表不得镜像余额。

2. **充值**一节扩展（不建表，只定归属）：

   > 未来 C 端/支付渠道充值：`recharge_order`（名待定）与微信/支付宝等适配代码均属 **billing** 域；回调验签、订单状态机、入账 `topup` 流水在 billing 内闭环。`auth` 只提供已认证 `user_id`，不实现支付。

3. 表字段里写 `FK →` 的地方加脚注或句：与 README 前向策略一致；逻辑关联 + 索引；存量物理 FK 不在本 WP 拆除。

### 8.8 修改 `docs/engineering/feature-briefs/README.md`

1. 在「怎么用」与「现有 brief」之间（或「现有 brief」表上方）增加注意：

   > **持久化**：工程目标为 MyBatis-Plus（[ADR 0006](../adr/0006-mybatis-plus-persistence.md)）。WP0–WP12 等历史 brief 若仍写 JdbcTemplate /「不引入 ORM」，以 ADR 0006 与 `TECH_STACK.md` 为准，**不必**回改那些历史 brief 正文。

2. 索引表新增一行：

   | [2026-10-25-wp13-docs-mybatis-plus-domain.md](./2026-10-25-wp13-docs-mybatis-plus-domain.md) | WP13 | 工程文档对齐：MyBatis-Plus + 领域分包（docs-first；ORM 代码迁移另 WP） | 已确认 / 实现中 |

3. 删除或改写「后续 WP13 在开工当天按模板新增，不要提前写空 brief」——WP13 已存在。

### 8.9 修改 `docs/engineering/DEVELOPMENT.md`

1. 工作包依赖图末尾增加 WP13（文档对齐；不阻塞已合并的 WP12，作为工程基线修正）。
2. 新增小节：

```markdown
## WP13 — 工程文档对齐（MyBatis-Plus + 领域分包）

- **里程碑**：工程真相源（跨 M；docs-first）
- **Brief**： [2026-10-25-wp13-docs-mybatis-plus-domain.md](./feature-briefs/2026-10-25-wp13-docs-mybatis-plus-domain.md)
- **做什么**：原地更新 TECH_STACK / ARCHITECTURE / ADR（含新 0006）/ data-model / briefs 索引等，冻结 MyBatis-Plus 与领域分包；澄清用户/钱包边界与前向逻辑外键策略。
- **不做**：全量 ORM 代码迁移；不改历史 WP0–WP12 brief 正文；不批量 DROP 物理 FK；不改成全局 MVC 目录。
- **完成**：文档与 ADR 0006 一致；`api/pom.xml` 不再写「不引入 ORM」为目标。
```

文首若写「WP0–WP11」，改为覆盖到 WP13（或「WP0–WP13」）。

### 8.10 修改 `docs/engineering/CONTRIBUTING.md`

在 PR 检查清单追加：

- [ ] 持久化与文档遵守 [ADR 0006](./adr/0006-mybatis-plus-persistence.md)（MyBatis-Plus 为目标；新代码不把「禁止 ORM」当冻结栈）。
- [ ] 未把 Java 包改成全局 `controller` / `service` / `mapper`；新代码落在领域包的 `web` / `application` / `domain` / `infra`。

### 8.11 修改 `api/pom.xml` 注释

将：

```xml
<!-- 用户表由 Flyway 建，JDBC 直连，不引入 ORM。 -->
```

改为例如：

```xml
<!-- 持久化目标：MyBatis-Plus（ADR 0006）。当前仍可能使用 spring-jdbc / JdbcTemplate 过渡；迁移完成前勿再写「禁止 ORM」。 -->
```

**本 WP 不要求**删除 `spring-boot-starter-jdbc`，**不要求**必须添加 mybatis-plus 依赖（添加则必须 `mvn -pl api -am test` 或项目惯用命令可通过）。

### 8.12 本 brief 入仓

将本文件复制为：

`docs/engineering/feature-briefs/2026-10-25-wp13-docs-mybatis-plus-domain.md`

---

## 9. 验收（严格勾选）

- [ ] 分支名为 `feat/wp13-docs-mybatis-plus-domain`（或 PR 说明等价）。
- [ ] `docs/engineering/adr/0006-mybatis-plus-persistence.md` 存在且状态 Accepted；含 Context / Decision / Consequences；Decision 含 MP、领域分包、显式 SQL 保留点、禁止全局 MVC。
- [ ] `docs/engineering/adr/README.md` 索引含 0006。
- [ ] `docs/engineering/adr/0002-java-spring-business-api.md` Decision 点名 MyBatis-Plus 并链接 0006。
- [ ] `docs/engineering/TECH_STACK.md` 业务 API 表含 MyBatis-Plus 冻结行；无「最终禁止 ORM」表述。
- [ ] `docs/engineering/ARCHITECTURE.md` 写清领域包 + 四层 + 跨域只经 application + infra 用 MP。
- [ ] `docs/engineering/data-model/README.md`：users 无余额；前向逻辑 FK 策略已写；存量物理 FK 不 drop。
- [ ] `docs/engineering/data-model/billing.md`：余额只在 wallet；充值订单/支付属 billing。
- [ ] `docs/engineering/feature-briefs/README.md`：顶部持久化说明 + WP13 索引行；无「WP13 尚未写」过时句。
- [ ] `docs/engineering/DEVELOPMENT.md` 有 WP13 小节。
- [ ] `docs/engineering/CONTRIBUTING.md` 检查清单含 ADR 0006 / 禁止全局 MVC。
- [ ] `api/pom.xml` 注释已去掉「不引入 ORM」目标表述。
- [ ] **没有**改 WP0–WP12 brief 正文去替换 Jdbc 字样（README 说明除外）。
- [ ] **没有**全量替换 `Jdbc*Repository` 为 Mapper；**没有**新 Flyway；**没有**改 `docs/content/**`。
- [ ] PR 描述引用本 brief 路径，列出验收勾选与「明确没做 ORM 代码迁移」。

---

## 10. 非目标（阶段稳定）

- 不 fork NewAPI；不建第二套用户额度账本（ADR 0001）。
- Worker 不 confirm/release 钱包（ADR 0004）。
- Admin 不做第二创作产品。
- 不以物理 FK CASCADE 删除账本流水。

---

## 11. 下一步（Out of scope / next WP，本 PR 不做）

建议 **WP14（名待定）：MyBatis-Plus 代码迁移**

- 引入 `mybatis-plus-boot-starter`（版本与 Boot 3 / Java 21 兼容，在该 brief 锁版本）。
- 按领域逐个替换：`billing` → `job` → `auth` → `project` / `admin` / `i18n`。
- Mapper 放各域 `infra`；保留 application 端口；钱包与 job 条件更新用 `@Update`。
- 单测 / 集成测行为与现网 Jdbc 路径对齐（freeze 不足、confirm/release 幂等、job 状态乐观锁）。
- 迁移完成后可删除纯 Jdbc 实现类；再评估是否保留 `spring-boot-starter-jdbc`。

---

## 12. 给 Minimax 的额外约束

- 遵守 `docs/engineering/TECH_STACK.md` 与全部 Accepted ADR（含本 WP 新增的 0006）。
- 不改无关文件；不重写 `docs/content/`。
- 密钥用占位符。
- **改历史文件，不要只写修订笔记。**
- 开 PR，标题建议：`docs(wp13): MyBatis-Plus + domain packaging truth source`。
