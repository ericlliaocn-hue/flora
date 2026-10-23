# Feature brief：WP1 API / Worker 骨架

- 日期：2026-10-24
- 作者：flora engineering
- 对应 WP / 里程碑：WP1 / M1
- 状态：已实现，待验收

WP0 只做了前端白标。本 brief 落地仓内第一组 **后端进程**：Java 21 + Spring Boot 3 的业务 API 与 **瘦 worker** 的可启动骨架。

本任务 **只做空骨架 + 健康检查 + 配置分层**，不做认证、Job、队列、钱包、Anytoken 调用、前端改打 API。

## 背景

`docs/engineering/TECH_STACK.md` 冻结的第一阶段栈：Java 21、Spring Boot 3、Sa-Token、PostgreSQL、Redis、火山 TOS、RabbitMQ。`docs/engineering/ARCHITECTURE.md` 定义 `api` 与 `worker` 是两个独立进程。

WP0 之后仓库只有前端（`web/`），没有任何服务端代码。WP1 要先把两个进程「立起来」：能本地启动、能各自报健康、不含任何业务逻辑。这样 WP2（认证）、WP3（Job 状态机 + Outbox + RabbitMQ）才有落点。

## 目标

1. 仓库出现两个可构建、可独立启动的模块：
   - `api/`：业务 API 进程
   - `worker/`：消费进程（本阶段无真实消费者，但必须能独立启动）
   - 根 `pom.xml` 作 Maven 聚合父工程（Gradle 未装，用等价清晰布局）
2. 两个进程本地都能启动，并各自暴露健康检查，返回 200。
3. 配置分层：`application.yml` + `application-local.yml`；密钥只走环境变量占位，仓库里没有真实 Key。
4. `docs/engineering/runbooks/local-dev.md` 补上真实可执行的启动步骤（JDK 21、端口、健康检查 URL），并诚实标注哪些依赖本阶段 **不** 需要。
5. 预留领域边界空包（`job` / `billing` / `gateway`），只写边界说明，不写实现。

## 范围

允许新增 / 修改：

- 根 `pom.xml`（聚合父工程）
- `api/`（Maven 模块 + 启动类 + 配置 + 预留空包）
- `worker/`（Maven 模块 + 启动类 + 配置 + 预留空包）
- `docs/engineering/runbooks/local-dev.md`（把「TBD：flora 业务进程」换成真实步骤）
- `docs/engineering/feature-briefs/2026-10-24-wp1-api-worker-skeleton.md`（本文件）
- `docs/engineering/feature-briefs/README.md`（状态表加一行）
- `.gitignore`（忽略 Maven 产物与本地密钥文件）

技术选型：Java 21、Spring Boot 3.5.x、Spring Actuator、Sa-Token（仅依赖与空配置）。

## 非范围

- Sa-Token 登录接口、账号、会话
- Job 表 / Flyway / 状态机 / Outbox / RabbitMQ 投递与消费者逻辑
- 钱包冻结 / 确认 / 释放
- Anytoken 调用、TOS 上传、STS、回调
- 前端改打 flora API
- Admin
- PostgreSQL / Redis / RabbitMQ 的本地 compose
- Docker 镜像与编排
- CI 工作流

## 数据表字段

无。本任务不建表、不引入 Flyway。

## API

无业务 API。本任务只暴露两个健康检查：

| 进程 | URL | 说明 |
| --- | --- | --- |
| `api` | `http://127.0.0.1:8080/actuator/health` | Spring Actuator，200 + `{"status":"UP"}` |
| `worker` | `http://127.0.0.1:8081/actuator/health` | 同上 |

端口可用 `FLORA_API_PORT` / `FLORA_WORKER_PORT` 覆盖。

## 状态机

无。

## 验收

- [x] 仓库出现可构建的 `api` 与 `worker`
- [x] `api` 能独立启动，健康检查 200
- [x] `worker` 能独立启动，健康检查 200
- [x] `docs/engineering/runbooks/local-dev.md` 有真实启动步骤
- [x] 无真实密钥入库；`.gitignore` 覆盖 local 密钥文件
- [x] 未实现登录 / Job / 队列 / 钱包 / Anytoken
- [x] 未改 `web/`；未改 `docs/content/**`
- [x] Worker 不依赖 `web/`、`canvas-agent/`、画布 / 创作代码
- [x] 没有上帝类；`job` / `billing` / `gateway` 只有边界说明

## 非目标

- 不在本任务把两个进程接成完整业务链路
- 不在本任务发布任何镜像或 compose 编排
- 不把「骨架能起」写成「托管闭环已可用」

## 给实现者的约束

- 遵守 `docs/engineering/TECH_STACK.md`：Java 21 + Spring Boot 3，不换栈、不加额外框架
- 不要为了「以后要用」提前加数据库 / 队列 / 对象存储依赖；本阶段两个进程 **不依赖** Postgres、Redis、RabbitMQ 即可启动
- Worker 保持瘦：不要让它依赖 `web/` 或任何画布 / 创作代码
- 密钥只走环境变量占位；仓库里不出现真实 Key、Token、密码
- 不要写上帝类；两个进程各自只有一个启动类
- 新的超时 / 重试 / 限额：本任务不需要，禁止顺便加
