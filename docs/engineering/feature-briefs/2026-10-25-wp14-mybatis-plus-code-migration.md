# Feature brief：WP14 MyBatis-Plus 代码迁移（Jdbc*Repository → Mapper，保留领域分包）

- 日期：2026-10-25
- 作者：flora engineering
- 对应 WP / 里程碑：WP14 / 工程实现（跨 M；接 WP13 ADR 0006）
- 状态：已确认（待实现）
- 建议分支：`feat/wp14-mybatis-plus-code-migration`
- 预计 PR 数：1（允许同一 PR 内按域分 commit；**不要**拆成「只加依赖、不换实现」的空 PR）

把本文件从「# Feature brief」起到文末 **整份粘贴** 给 Minimax / 云端代理。不要摘要。实现者只改本 brief 点名的范围；验收以「仓内编译测试通过 + 无残留业务 Jdbc 仓储 + 领域包布局不变」为准。

---

## 0. 给实施者（Minimax / cloud-agent）的先读指令

动工前**必须**按顺序读完：

1. `AGENTS.md`
2. `docs/engineering/TECH_STACK.md`（持久化行：MyBatis-Plus）
3. `docs/engineering/ARCHITECTURE.md`（领域包 + 四层）
4. `docs/engineering/adr/0006-mybatis-plus-persistence.md`（**本 WP 的硬法**）
5. `docs/engineering/adr/0002-java-spring-business-api.md`
6. `docs/engineering/adr/0004-thin-worker-no-god-classes.md`（worker 仍不得 confirm/release 钱包）
7. `docs/engineering/data-model/billing.md` + `docs/engineering/data-model/README.md`
8. `docs/engineering/feature-briefs/2026-10-25-wp13-docs-mybatis-plus-domain.md`（「下一步」即本 WP）
9. `pom.xml`（Spring Boot **3.5.16**、Java **21**）、`api/pom.xml`、`worker/pom.xml`

**本 WP 的硬约束：**

- **做代码迁移**：引入 MyBatis-Plus，替换 `api` / `worker` 中基于 `JdbcTemplate` 的业务仓储实现。
- **包结构不变**：继续 `com.flora.api.<domain>.{web,application,domain,infra}`（worker 侧保持现有 `com.flora.worker...infra`）。**禁止**改成全局 `controller` / `service` / `mapper`。
- **跨域**：仍只经对方 `application`；禁止 `job` 直接依赖 `billing` 的 Mapper。
- **显式 SQL 保留**：钱包 `freeze` / `confirm` / `release`（含 `wallet` / `wallet_hold` 条件更新）与 job 乐观状态迁移**必须**用 `@Update` / `@Select` / XML 的显式 `WHERE …`，禁止「load → 改字段 → `updateById`」丢掉条件。
- **不要**新 Flyway、**不要**批量 DROP 物理 FK、**不要**改 `docs/content/**`、**不要**重写 WP15 analytics 文档 / ADR 0007、**不要**改公开 HTTP 契约与前端。
- **不要**回改 WP0–WP12 / WP13 / WP15 历史 brief 正文（只允许本 brief 入仓 + 索引 / DEVELOPMENT 指向 WP14）。

---

## 1. 背景

WP13 已把工程真相源改成「目标持久化 = MyBatis-Plus + 领域分包」（ADR 0006）。仓内 `api` / `worker` 仍大量 `JdbcTemplate` + `Jdbc*Repository`（auth 的 `UserRepository` 虽未用 `Jdbc` 前缀，同样是 Jdbc 实现）。

本 WP 把过渡实现换成 MyBatis-Plus，使代码与文档一致。表结构、HTTP、钱包/job 语义不变——只换 `infra` 怎么访问库。

---

## 2. 目标（可验证）

1. **锁定依赖（Boot 3 专用）**：
   - Artifact：**`com.baomidou:mybatis-plus-spring-boot3-starter`**
   - Version：**`3.5.17`**（与 Spring Boot 3.x / Java 21 兼容；**禁止**误用 Boot2 的 `mybatis-plus-boot-starter`）
   - 在 `api/pom.xml` 与 `worker/pom.xml` 均引入（版本可用父 POM `dependencyManagement` / property `mybatis-plus.version=3.5.17` 统一）。
   - **不要**再单独引入冲突的 `mybatis-spring-boot-starter` / 另一份 MyBatis 核心。
2. `@MapperScan`（或等价自动配置）覆盖 api / worker 的 Mapper；应用能启动。
3. 下列库存**全部**迁离 `JdbcTemplate` 业务 SQL（见 §8 清单）；删除对应纯 Jdbc 实现类（或等价：类不再存在且无引用）。
4. **保留**各域 `*Repository` 端口（interface）时：application / 单测的 InMemory 实现仍可编译；生产实现改为「Mapper + 薄适配」。
5. 钱包条件更新与 job 乐观状态 SQL 语义与迁前一致（见 §7）。
6. `mvn -pl api,worker -am test`（或仓内惯用等价命令）**通过**。
7. 文档：`DEVELOPMENT.md` WP14 小节从「预留」改为指向本 brief；`feature-briefs/README.md` 增加 WP14 行；本 brief 入仓。
8. `api/pom.xml` / `worker/pom.xml` 注释与依赖反映「已用 MyBatis-Plus」；`spring-boot-starter-jdbc` 可作为 MP 传递依赖保留，**不必**为「纯净」强删（若删除须证明仍可运行）。

---

## 3. 范围（允许新增 / 修改）

| 路径 | 动作 |
| --- | --- |
| `pom.xml` | 可选：`mybatis-plus.version` + `dependencyManagement` |
| `api/pom.xml`、`worker/pom.xml` | 增加 `mybatis-plus-spring-boot3-starter:3.5.17`；更新注释 |
| `api/src/main/java/com/flora/api/**/infra/**` | Mapper + 替换 Jdbc 实现；可增 `config`（MapperScan） |
| `api/src/main/java/com/flora/api/auth/infra/UserRepository.java` | 迁离 JdbcTemplate（拆端口+实现或内部改 Mapper） |
| `worker/src/main/java/com/flora/worker/**/infra/**` | 同上 |
| `api/src/main/resources/**`、`worker/src/main/resources/**` | 可选 `application.yml` mybatis-plus 段；可选 `mapper/**/*.xml`（优先注解，复杂 SQL 才 XML） |
| `api/src/test/**`、`worker/src/test/**` | 仅当编译/装配需要时调整；InMemory* 测应继续绿 |
| `docs/engineering/feature-briefs/2026-10-25-wp14-mybatis-plus-code-migration.md` | **新增**（本文件全文） |
| `docs/engineering/feature-briefs/README.md` | 索引 + WP14 |
| `docs/engineering/DEVELOPMENT.md` | WP14 小节落地（不再「编号预留」） |

---

## 4. 非范围（本 PR 明确不做）

- 改成全局 `controller` / `service` / `mapper` 目录。
- 新 Flyway 迁移；批量 DROP / 改造物理 FK。
- 重写 analytics（WP15 / ADR 0007）、新建 `analytics_event` 表、ads/campaign。
- 改 `web/` 前端、改公开 REST 路径/字段、改 Anytoken / TOS 业务逻辑。
- Worker 染指钱包 confirm/release（ADR 0004）。
- 回改 WP0–WP13、WP15 brief 正文。
- 「顺便」大重构 application / domain 状态机；本 WP 只换持久化实现。
- 引入第二套 ORM（JPA/Hibernate）或第二套账本。

---

## 5. 数据表字段

**无表结构变更。** 不新增 / 不修改 Flyway。

实体 / `@TableName` 映射必须与现有列一致（见 `docs/engineering/data-model/`）。特别注意：

- 余额只在 `wallet.available` / `wallet.frozen`
- UUID 主键、`timestamptz`、json/jsonb 列：类型映射要正确（Postgres）

---

## 6. API

**无 HTTP API 变更。** 行为与现网等价。

---

## 7. 状态机 / 显式 SQL（不可退化）

下列 SQL 意图必须原样保留为显式语句（注解或 XML），验收时 diff 语义：

### 7.1 钱包（billing）

| 操作 | 现有意图（见 `JdbcWalletRepository` / `JdbcWalletHoldRepository`） |
| --- | --- |
| freeze | `UPDATE wallet SET available = available - ?, frozen = frozen + ? … WHERE id = ? AND available >= ?` |
| credit / topup 类 | `available = available + ?`（保持现方法语义） |
| confirm 扣冻 | `frozen = frozen - ? … WHERE id = ? AND frozen >= ?` |
| release | `available = available + ?, frozen = frozen - ? … WHERE id = ? AND frozen >= ?` |
| hold 终结 | `UPDATE wallet_hold SET status = ?, finalized_at = now() WHERE id = ? AND status = 'frozen' …`（幂等闸门） |

禁止用 MP 自动填充 + `updateById` 替代上述条件更新。

### 7.2 Job（api + worker）

带前置状态的更新（例：`WHERE id = ? AND status = ?` / `queued → running`）必须显式保留。outbox / provider_task / idempotency 的条件更新同理。

---

## 8. 库存清单（开工时必须再扫一遍）

### 8.1 如何重新发现（实施者必做）

在仓库根执行（或等价 `rg`）：

```bash
# 所有 Jdbc* 仓储
find api worker -name 'Jdbc*.java' | sort

# 凡使用 JdbcTemplate 的业务类（含未命名 Jdbc* 的 auth）
rg -l 'JdbcTemplate' api/src/main worker/src/main --glob '*.java' | sort
```

以命令输出为准；若 main 上新增了类，一并迁移。下文是 **2026-10-25 main（WP13/WP15 已合）** 的基线清单。

### 8.2 api — 必须迁移

| 域 | 类 | 备注 |
| --- | --- | --- |
| **billing**（先做） | `JdbcWalletRepository` | 实现 `WalletRepository`；freeze/confirm/release |
| billing | `JdbcWalletHoldRepository` | `finalizeIfFrozen` 等 |
| billing | `JdbcWalletLedgerRepository` | |
| **job** | `JdbcJobRepository` | 乐观状态 |
| job | `JdbcJobStepRepository` | |
| job | `JdbcOutboxRepository` | |
| job | `JdbcProviderTaskRepository` | |
| job | `JdbcIdempotencyRepository` | |
| **auth** | `UserRepository` | **无 Jdbc 前缀**，但是 `JdbcTemplate` 实现；须迁 |
| **project** | `JdbcCanvasProjectRepository` | |
| project | `JdbcUserAssetRepository` | |
| **admin** | `JdbcAdminUserRepository` | |
| admin | `JdbcAdminJobRepository` | |
| admin | `JdbcModelDisplayConfigRepository` | |
| **i18n** | `JdbcI18nMessageRepository` | |

### 8.3 worker — 必须迁移（同一 WP）

ADR 0006：「需要访问同一业务库的 JVM 模块」。worker 四个类一并迁：

| 类 |
| --- |
| `JdbcWorkerJobRepository` |
| `JdbcWorkerJobStepRepository` |
| `JdbcProviderTaskRepository` |
| `JdbcWorkerOutboxRepository` |

### 8.4 推荐落地形状（每个仓储）

```
<domain>/infra/
  XxxRepository.java          ← 端口（已有则保留）
  XxxMapper.java              ← @Mapper + @Select/@Insert/@Update …
  MybatisXxxRepository.java    ← @Repository，实现端口，委托 Mapper
  （删除 JdbcXxxRepository.java）
```

auth 若暂无 interface：可抽 `UserRepository` 为 interface + `MybatisUserRepository`，或保留类名但内部改为 Mapper（须删除对 `JdbcTemplate` 的字段依赖）。

**顺序建议（可同一 PR 多 commit）：** billing → job（api）→ auth → project → admin → i18n → worker。

---

## 9. 逐项修改说明书

### 9.1 父 POM / 模块依赖

在 `pom.xml` `properties` 增加：

```xml
<mybatis-plus.version>3.5.17</mybatis-plus.version>
```

在 `dependencyManagement` 增加：

```xml
<dependency>
  <groupId>com.baomidou</groupId>
  <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
  <version>${mybatis-plus.version}</version>
</dependency>
```

`api/pom.xml` 与 `worker/pom.xml`：

```xml
<dependency>
  <groupId>com.baomidou</groupId>
  <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
</dependency>
```

注释改为例如：`持久化：MyBatis-Plus 3.5.17（ADR 0006 / WP14）。`

**禁止**使用 `mybatis-plus-boot-starter`（那是 Spring Boot 2）。

### 9.2 Mapper 扫描

- api：在 `ApiApplication` 或独立 `@Configuration` 上  
  `@MapperScan("com.flora.api")`  
  （仅接口带 `@Mapper`；不要把普通 `@Repository` 扫成 Mapper）
- worker：对 `com.flora.worker` 同样处理。

可选 `application.yml`：

```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      table-underline: true
```

（列名若已与现 SQL 一致，勿开启会破坏映射的激进策略；以现有列为准。）

### 9.3 Postgres `INSERT … RETURNING`

现有 Jdbc 多用 `RETURNING`。MyBatis 侧推荐：

- 用 `@Select("INSERT … RETURNING …")` 直接映射实体，或
- `@Insert` + `<selectKey>` / `@Options` 且验证 UUID 默认值 `gen_random_uuid()` 行为与迁前一致。

不得改成「先插再查」却丢失原子性（尤其钱包/hold 与 job 同事务路径）。

### 9.4 事务

保持 Spring `@Transactional` 边界在 **application** 层（现有 freeze+ledger+hold、submit job 等）。Mapper 方法本身不要随便加导致自调用失效的事务注解来「修补」。

### 9.5 测试

- 现有大量 **InMemory\*** 单测：应继续通过；不要强行改成必须连 Postgres。
- 若新增 `@MybatisPlusTest` / Testcontainers：可选，**非**本 WP 强制；强制项是 **全量 `mvn test` 绿** + 显式 SQL 语义保留。
- 删掉对已删除 `Jdbc*` 类的反射/直接引用。

### 9.6 文档

1. 本 brief → `docs/engineering/feature-briefs/2026-10-25-wp14-mybatis-plus-code-migration.md`
2. `feature-briefs/README.md` 表增加 WP14 行（状态：实现中/已确认）
3. `DEVELOPMENT.md`「WP14 — MyBatis-Plus 代码迁移」改为有 Brief 链接、做什么/不做/完成标准（去掉「编号预留」「待开」）

---

## 10. 验收（严格勾选）

- [ ] 分支名 `feat/wp14-mybatis-plus-code-migration`（或 PR 说明等价）。
- [ ] 依赖为 **`mybatis-plus-spring-boot3-starter`:`3.5.17`**（api + worker）；**没有**误加 `mybatis-plus-boot-starter`。
- [ ] `rg -l 'JdbcTemplate' api/src/main worker/src/main --glob '*.java'`：**无**业务仓储命中（允许测试夹具；main 源码应清空）。若残留，PR 必须解释且不得是未迁完的 `Jdbc*Repository` / `UserRepository`。
- [ ] `find api worker -name 'Jdbc*.java'`：**无**文件（main；测试目录也不应再放生产 Jdbc 实现）。
- [ ] billing 钱包 freeze/confirm/release 与 hold `status='frozen'` 条件更新仍为显式 SQL。
- [ ] job（api + worker）乐观状态更新仍为显式 SQL。
- [ ] **未**改成全局 MVC 三目录；新类仍在各域 `infra`（或 `infra` 下 mapper 子包）。
- [ ] **未**新增/修改 Flyway；**未** DROP 物理 FK。
- [ ] **未**改 WP15 analytics 文档实质内容；**未**改 `docs/content/**`。
- [ ] application 仍只依赖 Repository 端口（或与迁前同等抽象）；**无** `job` → `billing` Mapper 直连。
- [ ] `mvn -pl api,worker -am test`（或文档中的等价命令）通过。
- [ ] 本 brief 已入仓；`DEVELOPMENT.md` / `feature-briefs/README.md` 已指向 WP14。
- [ ] PR 描述引用本 brief，列出验收勾选，并写明「未做：FK 拆除 / analytics / 全局 MVC / HTTP 变更」。

---

## 11. 非目标（阶段稳定）

- 不 fork NewAPI；不建第二套用户额度账本（ADR 0001）。
- Worker 不 confirm/release 钱包（ADR 0004）。
- 不以物理 FK CASCADE 删流水。
- 不借本 WP 做充值订单表或支付渠道对接（仍属后续 billing 功能 WP）。

---

## 12. 下一步（Out of scope）

- 充值订单 `recharge_order` + 支付适配（billing 功能 WP）。
- analytics Phase 2 ingest（若产品需要）。
- 评估是否对存量物理 FK 做弱化迁移（另 brief）。
- 可选：为钱包/job 补 Testcontainers 级持久化回归。

---

## 13. 给 Minimax / cloud-agent 的额外约束

- 遵守 `docs/engineering/TECH_STACK.md` 与全部 Accepted ADR（尤其 **0006**、0002、0004）。
- 密钥用占位符；不提交真实 Anytoken / TOS / DB 密码。
- 不改无关文件；一次 PR 专注迁移。
- 开 PR，标题建议：`feat(wp14): migrate Jdbc repositories to MyBatis-Plus 3.5.17`。
