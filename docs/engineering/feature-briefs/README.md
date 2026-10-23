# Feature briefs

Brief 是 flora 的需求源。Minimax 与人实现都从这里粘贴，不从聊天里重新编造范围。

## 怎么用

1. 复制 [_TEMPLATE.md](./_TEMPLATE.md) 为 `YYYY-MM-DD-<wp-or-slug>.md`。
2. 填完「范围 / 非范围 / 验收 / 非目标」再开工。标了 TBD 的字段级细节不要让实现者猜。
3. 把全文粘贴给 Minimax，并声明必须遵守 `docs/engineering/` 的 ADR 与冻结栈。
4. PR 引用本文件路径；审查只对照 brief。

## 现有 brief

| 文件 | WP | 状态 |
| --- | --- | --- |
| [2026-10-24-wp0-white-label-flora.md](./2026-10-24-wp0-white-label-flora.md) | WP0 | 已实现，待验收 |
| [2026-10-24-wp1-api-worker-skeleton.md](./2026-10-24-wp1-api-worker-skeleton.md) | WP1 | 已实现，待验收 |

后续 WP1–WP10 在开工当天按模板新增，不要提前写空 brief。
