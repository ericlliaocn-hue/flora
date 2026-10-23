# 里程碑 M0–M4

本页是已对齐的阶段目标与退出标准。工作包拆分见 [DEVELOPMENT.md](./DEVELOPMENT.md)。未写进退出标准的能力，默认不算该里程碑完成。

当前仓库仍是 infinite-canvas 前端 fork：没有 Java API、没有 worker、没有 flora 账本。M0 从品牌与工程纪律开始，不假装后端已存在。

## 总览

| 里程碑 | 主题 | 主要工作包 | 用户能感知的结果 |
| --- | --- | --- | --- |
| M0 | 身份与工程纪律 | WP0 + 本文档树 | 产品叫 flora；大改动必须先有 brief |
| M1 | 平台骨架 | WP1–WP5 | 登录后能提交任务；worker 只处理 `job_id` |
| M2 | 付费出图闭环 | WP6–WP9 | 预付费生图成功扣费、失败退冻；错误友好 |
| M3 | C 端最小产品 | WP10 | 画布/素材/生成记录能跟账号走 |
| M4 | 运维面 | WP11 | 运营能配置与排障，而不是多一个创作产品 |

## M0 — 身份与工程纪律

**目标**：把 fork 收成 flora 产品仓库，并锁住后续协作方式。

**必须完成**

- 用户可见品牌改为 flora（标题、logo、主文案）。详见 [WP0 brief](./feature-briefs/2026-10-24-wp0-white-label-flora.md)。
- `docs/engineering/` 成为架构、里程碑、ADR、brief 的唯一入口。
- 大改动强制：feature brief → 实现 → PR 对照 brief review。

**退出标准**

- 打开 `web/` 开发页，浏览器标题与主界面不再把「无限画布 / Infinite Canvas」当产品名。
- 根 README 顶部有 flora 说明，并链到本目录。
- `AGENTS.md` 要求 agent 先读本目录，大改动先写 brief。
- 公开文档站点（`docs/content/`、Next.js、Dockerfile）没有被掏空或改成工程站。

**明确不做**

- 不在 M0 引入 Java API、队列、计费。
- 不重写上游功能说明与赞助墙全文；只加 flora 入口。

## M1 — 平台骨架

**目标**：业务 API + 瘦 worker + Anytoken hop 能跑通一条「假任务也算任务」的状态机。

**必须完成**

- Java 21 + Spring Boot 3 业务 API；Sa-Token 登录。
- Postgres / Redis 落地；job / job_step / outbox / idempotency 表存在，字段级见 [data-model](./data-model/)（WP2 已锁定）。
- RabbitMQ 作为第一期队列；消息体只有 `job_id`。
- Worker 按 [ADR 0004](./adr/0004-thin-worker-no-god-classes.md) 保持瘦：不拉画布、不编排创作、不持有钱包。
- 只通过 Anytoken 访问模型；禁止 NewAPI fork 与双账本。

**退出标准**

- 已登录用户可以 `submit` 一条任务并 `query` 到 `queued → running → succeeded | failed`。
- 失败且 `attempt < max` 时回到 `queued`；超过后进入 `dead`（DLQ）。
- 取消路径能到 `cancelled`。
- 日志里看得到上游细节；查询接口对用户不回传原始供应商错误串。
- 代码审查能指出：没有 `TaskCenterService` 式 3k 行上帝类，没有 worker 依赖画布模块。

**明确不做**

- 不接真实扣费（那是 M2）。
- 不做 admin。
- 不要求画布云端持久化。

## M2 — 付费出图闭环

**目标**：预付费用户在画布上出一张图，账对、文件在、错误可读。

**必须完成**

- 钱包：提交冻结 → 成功确认 → 失败/取消释放。
- 前端托管模式改为打 flora API，不再把供应商 Key 放在浏览器里作为正式路径。
- Worker 调 Anytoken 真实生图；结果进火山 TOS。
- 用户可见错误：稳定 `error_code` + 友好文案；上游原文只在日志 / `provider_task`。
- 动态运营文案走 `i18n_message`；静态 UI 仍走前端 locale。先做 zh + en。

**退出标准**

- 余额不足：冻结失败，不创建可执行任务（或创建后立即失败且不入队执行），用户看到稳定错误码。
- 出图成功：TOS 可取回；钱包冻结转为确认；任务 `succeeded`。
- 上游失败或用户取消：冻结释放；任务 `failed` / `cancelled` / `dead` 符合状态机。
- 同一 `idempotency_key` 不重复扣两笔冻。
- 画布上至少一条官方生图路径走新链路。

**明确不做**

- 不铺视频/音频全模态（除非同一套 job 合同零成本捎带；默认只锁生图）。
- 不做社区、不做作品广场。
- 不做完整 admin。

## M3 — C 端最小产品

**目标**：C 端可以当「托管 flora」用，而不只是「能出一张图的演示」。

**必须完成**

- 画布项目、素材、生成记录与账号绑定并可在多设备取回（具体表字段在后续 brief 里定，不在本页锁死）。
- 前端不再把「仅本地 IndexedDB」写成已支持云同步的完成态；以实际能力更新工程说明。
- zh / en 覆盖静态 UI 与动态错误。
- C 端最小路径不依赖 admin：默认模型与策略可用配置或环境注入。

**退出标准**

- 用户换浏览器登录后能看到自己的项目/素材/生成记录（范围以当时 brief 为准，但必须有账号级持久化，而不是纯本地）。
- 没有 admin 也能完成：注册/登录 → 充值或加额度（可先用运维脚本）→ 画布出图 → 再次打开仍在。
- 公开用户文档若提到存储，不得声称「已云同步」除非该能力已验收。

**明确不做**

- 不把 admin 做成 M3 的阻塞项。
- 不做第二套创作 UI。

## M4 — 运维面（Admin）

**目标**：让运营能配置和排障。Admin 是 ops/config，不是第二个 flora。

**必须完成**

- 用户/钱包只读与有限调整（加额、冻结排查）。
- 任务查询、重试/死信查看、`provider_task` 原文仅对内部角色可见。
- 模型/渠道展示配置、功能开关、`i18n_message` 维护。
- 不引入独立创作画布、不引入 C 端信息流。

**退出标准**

- 运营不连数据库也能：查用户、查任务、看失败原因、改一条动态文案、上下架一个模型展示项。
- 代码与 IA 上 admin 是「后台」，路由/权限与 C 端隔离。
- 没有把 C 端画布功能复制进 admin。

**明确不做**

- 不做运营自己的无限画布。
- 不做面向 C 的 CMS/社区。

## 跨里程碑不变式

这些条件从 M1 起就生效，后续里程碑不能破坏：

- 模型请求只经 Anytoken，见 [ADR 0001](./adr/0001-anytoken-not-newapi-fork.md)。
- 业务 API 是 Java 21 + Spring Boot 3，见 [ADR 0002](./adr/0002-java-spring-business-api.md)。
- 队列实现可换，job 模型不绑死 RabbitMQ，见 [ADR 0003](./adr/0003-rabbitmq-first-queue-agnostic-jobs.md)。
- Worker 禁止变胖，见 [ADR 0004](./adr/0004-thin-worker-no-god-classes.md)。
- 用户错误与上游原文分离，见 [ADR 0005](./adr/0005-friendly-errors-vs-raw-upstream.md)。
- 计费只有冻结 / 确认 / 释放，见 [data-model/billing.md](./data-model/billing.md)。
