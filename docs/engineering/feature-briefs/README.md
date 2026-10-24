# Feature briefs

Brief 是 flora 的需求源。Minimax 与人实现都从这里粘贴，不从聊天里重新编造范围。

## 怎么用

1. 复制 [_TEMPLATE.md](./_TEMPLATE.md) 为 `YYYY-MM-DD-<wp-or-slug>.md`。
2. 填完「范围 / 非范围 / 验收 / 非目标」再开工。标了 TBD 的字段级细节不要让实现者猜。
3. 把全文粘贴给 Minimax，并声明必须遵守 `docs/engineering/` 的 ADR 与冻结栈。
4. PR 引用本文件路径；审查只对照 brief。

## 现有 brief

| 文件 | WP | 说明 | 状态 |
| --- | --- | --- | --- |
| [2026-10-24-wp0-white-label-flora.md](./2026-10-24-wp0-white-label-flora.md) | WP0 | — | 已实现，待验收 |
| [2026-10-24-wp1-api-worker-skeleton.md](./2026-10-24-wp1-api-worker-skeleton.md) | WP1 | — | 已实现，待验收 |
| [2026-10-24-wp2-phase1-schema.md](./2026-10-24-wp2-phase1-schema.md) | WP2 | — | 已实现，待验收 |
| [2026-10-24-wp3-auth-users.md](./2026-10-24-wp3-auth-users.md) | WP3 | — | 已实现，待验收 |
| [2026-10-24-wp4-job-outbox-rabbit.md](./2026-10-24-wp4-job-outbox-rabbit.md) | WP4 | — | 已实现，待验收 |
| [2026-10-24-wp5-thin-worker-anytoken.md](./2026-10-24-wp5-thin-worker-anytoken.md) | WP5 | — | 已实现，待验收 |
| [2026-10-24-wp6-prepaid-wallet.md](./2026-10-24-wp6-prepaid-wallet.md) | WP6 | — | 已实现 |
| [2026-10-24-wp7-web-flora-api-generation.md](./2026-10-24-wp7-web-flora-api-generation.md) | WP7 / M2 | — | 已实现 |
| [2026-10-24-wp8-tos-storage-signed-url.md](./2026-10-24-wp8-tos-storage-signed-url.md) | WP8 / M2 | — | 已实现（待验收） |
| [2026-10-24-wp9-friendly-errors-i18n-message.md](./2026-10-24-wp9-friendly-errors-i18n-message.md) | WP9 / M2 | — | 已实现（待验收） |
| [2026-10-24-wp10-cloud-persistence.md](./2026-10-24-wp10-cloud-persistence.md) | WP10 / M3 | C 端账号级云端持久化（项目 + 资产 + 历史 + TOS 虚拟主机域名兼容 + Anytoken 真实模型对齐） | 已实现（待验收） |

后续 WP11 在开工当天按模板新增，不要提前写空 brief。上表里的行是 **真实 brief**（已落盘、可整份粘贴给 Minimax），不是占位行。
