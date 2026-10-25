# ADR 0001 — 只用 Anytoken，不 fork NewAPI

- 状态：Accepted
- 日期：2026-10-23

## 背景（Context）

pilipala 需要调用多家模型做生图等能力。常见捷径是 fork NewAPI / qfzy-api，在业务仓库里同时拥有「用户、额度、渠道、上游协议」。qingfeng 路径还出现过双适配器：业务一边对接自研网关，一边再接一套聚合 API，用户与配额在两边各记一份。

结果是：对账失败、错误语义分裂、升级网关等于升级整个业务。pilipala 作为白标托管产品，真正要拥有的是画布会话、任务和预付费钱包，不是再做一个模型网关。

## 决策（Decision）

- 模型流量 **只** 经过 **Anytoken**。pilipala worker 只实现一个 Anytoken 客户端。
- **不** fork、不内嵌、不维护 NewAPI / qfzy-api。
- **不** 在 Anytoken 与 pilipala 之间做双用户、双额度、双账单。用户余额只存在 pilipala 钱包。
- 浏览器与 C 端 API 都不暴露 Anytoken 凭证。

## 后果（Consequences）

- 正向：账本清晰；换模型/渠道是 Anytoken 侧运营动作；业务发布不必跟网关 fork 版本绑死。
- 正向：内部 DTO 可以稳定（submit/query/result），上游协议变化被 hop 挡住。
- 代价：Anytoken 不可用时 pilipala 不能「临时改打官方 API」作为产品特性。应急只能是运维切换 Anytoken 渠道，而不是在代码里加第二适配器。
- 代价：Anytoken 自身的额度是公司成本，需要运营单独看，不映射成 C 端钱包明细。
- 违反本 ADR 的 PR（新增 NewAPI 模块、双写配额）直接拒绝。
