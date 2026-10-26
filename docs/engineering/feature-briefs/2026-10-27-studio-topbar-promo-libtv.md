# Feature brief：Studio 顶栏 + 活动条 LibTV 复刻（配置表控制）

- 日期：2026-10-27
- 作者：cavas / pilipala engineering
- 对应：Studio chrome polish（承接 WP18 `app_runtime_setting`）
- 状态：已确认（实现中）
- 参考截图：LibTV 顶部青绿色倒计时条 + 黑底顶栏「企业采购 / 积分超市 / 开通会员（限时折扣角标） / 注册登录」
- 用户锁定：A+B；**开关与文案走 `app_runtime_setting` 配置表**（不是只靠 Vite env）

## 目标

1. **顶栏**（`StudioUtilityBar`）气质对齐 LibTV：企业采购、积分超市、开通会员（带限时折扣小角标）、右侧登录/注册或账号头像；保留已有登录态积分胶囊 `⚡` 与账号菜单（#44）。
2. **活动条**（`StudioPromoStrip`）做成 LibTV 风格白底黑字数字格倒计时 + 白底 CTA + 可关闭；**默认开启**，但开启/截止/文案可由配置表覆盖。
3. **禁止**复制 LibTV / Seedance 等商标与活动图；文案用 pilipala 自有预付费/会员叙事。

## 配置表（`app_runtime_setting`）

新增 key（Flyway `V13__studio_chrome_settings.sql`），默认值如下：

| key | 默认 value | 说明 |
| --- | --- | --- |
| `studio.promo.enabled` | `true` | 活动条总开关 |
| `studio.promo.end_at` | `2026-11-15T15:59:59.000Z` | ISO8601 截止；过期不展示 |
| `studio.promo.title` | `预付费加赠限时开启` | 粗体主标题（中文） |
| `studio.promo.copy` | `充值加赠最高 20%，任务成功才扣费` | 副文案 |
| `studio.promo.cta` | `限时抢购` | 白底按钮 |
| `studio.membership.badge` | `限时 4.6 折` | 「开通会员」右侧小角标；空字符串则不显示角标 |
| `studio.membership.enabled` | `true` | 是否展示「开通会员」入口 |

公开只读接口（给前端拉配置，免登录）：

`GET /api/v1/studio/features` →

```json
{
  "ok": true,
  "data": {
    "promo_enabled": true,
    "promo_end_at": "2026-11-15T15:59:59.000Z",
    "promo_title": "...",
    "promo_copy": "...",
    "promo_cta": "...",
    "membership_enabled": true,
    "membership_badge": "限时 4.6 折"
  }
}
```

实现参考 `EmailAuthPolicy` / `GET /api/v1/auth/features`：读 `app_runtime_setting`，缺 key 用上表默认；Jackson snake_case。

兼容：若公开接口失败，前端回退到现有 i18n + `VITE_STUDIO_PROMO_*`（但 **默认展示逻辑以配置表 `studio.promo.enabled=true` 为准**，不再依赖 `VITE_STUDIO_PROMO_STRIP===true` 才显示；Vite 仅作本地覆盖可选）。

## 顶栏 UI（`studio-utility-bar.tsx`）

从左到右（右对齐簇，移动端图标+短 label）：

| 控件 | 文案（zh） | 行为 |
| --- | --- | --- |
| QuietLink | 企业采购 | 保留现 mailto / `studio.enterpriseEmail`；图标 Building2 |
| QuietLink | 积分超市 | 未登录 → 打开登录弹层；已登录 → 打开已有 `WalletBalancePopover` |
| Membership CTA | 开通会员 + 角标（配置表 `membership_badge`） | 点击走现有 `onRecharge`（充值/开通会员同一路径，本 WP 不新建支付页） |
| `⚡` 胶囊 | 仅登录后 | 已有 #44 行为 |
| 登录/注册 或 头像菜单 | 已有 #44 | 白底实心 pill / `AccountMenuPopover` |
| Agent 按钮 | 已有 | 保留 |

视觉：黑底顶栏；QuietLink 带细边框胶囊（贴近 LibTV「企业采购/积分超市」）；开通会员按钮内嵌青蓝小角标（`bg-[#1a8a84]` 或接近截图蓝，pilipala teal 系即可）；登录 pill 白底深字（`studio-solid-on-white`）。

i18n：更新 `studio.utilities.enterprise` →「企业采购」、`points` →「积分超市」、新增 `membership` →「开通会员」；en 对应 Enterprise / Points store / Membership。

## 活动条 UI（`studio-promo-strip.tsx`）

- 青绿实心底（约 `#0d6b6b` / `#0e7a78`），高度约 40px。
- **倒计时**：每位数字独立白底圆角小方格 + 黑字（天:时:分:秒），格间冒号浅色；不要整条药丸包住。
- 文案：配置表 title（白/粗）+ copy。
- CTA：白底黑字圆角「限时抢购」→ `onRecharge`。
- 右上角 X 关闭；localStorage dismiss key 保留；过期或 `promo_enabled=false` 不渲染。
- 去掉「必须 VITE_STUDIO_PROMO_STRIP=true」门闩；改为拉 `/api/v1/studio/features`。

## 范围

允许改：

- Flyway V13 seed keys
- `api/...` StudioFeatures / RuntimeSetting 读取（可复用现有 setting repo）
- `web/.../studio-promo-strip.tsx`、`studio-utility-bar.tsx`、i18n、可选 thin client `fetchStudioFeatures`
- `CHANGELOG.md` Unreleased 一行
- 必要单测（API 默认值 + 前端 features 解析）

## 非范围

- 真实会员 SKU / 新支付页 / 积分商城商品页
- 复制 LibTV / Seedance 商标与素材
- 改侧栏 logo 布局（本 WP 顶栏左侧不加独立 LibTV 式大 logo；侧栏已有品牌）
- Admin 配置后台 UI（本 WP 只 DB/API；改值用 SQL）

## 验收

- [ ] 未登录顶栏无假 `⚡ 100`；有 企业采购 / 积分超市 / 开通会员(+角标) / 登录注册
- [ ] 活动条默认可见（配置表 true），白底数字格倒计时 + 白 CTA + 可关
- [ ] `GET /api/v1/studio/features` 返回上表字段；改 DB key 后刷新可见（可接受短缓存或无缓存）
- [ ] `promo_enabled=false` 或过期 → 无活动条；`membership_badge=""` → 无角标
- [ ] `bun test` + 相关 api 测试通过；PR 不自行 merge（由 cavas 合入部署）

## 分支

`feat/studio-topbar-promo-libtv`
