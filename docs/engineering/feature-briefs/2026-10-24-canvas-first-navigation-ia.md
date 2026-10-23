# Feature brief：画布优先的顶层导航与入口 IA 收敛

- 日期：2026-10-24
- 作者：flora engineering
- 对应 WP / 里程碑：既有 WP0–WP10 未覆盖前端 IA（新增前端体验工作项，暂记 FE-1）；落在 M0 收尾，服务 M3「C 端最小产品」
- 状态：已确认（可直接粘贴给 Minimax）

把本文件从「# Feature brief」起到文末 **整份粘贴** 给 Minimax。不要摘要成几句话。仓库 https://github.com/ericlliaocn-hue/flora ，前端目录 `web/`，产品名 **flora**（小写）。本任务 **只做前端信息架构**，不做后端、不碰生成链路。

## 背景

flora 的定位是「画布优先」的连续创作工作台（`docs/engineering/VISION.md`：画布是一等公民；「竞品拥有流量或工作流运行时；flora 拥有画布会话、任务账本和预付费」）。但前端目前仍是上游 infinite-canvas 的**多工具并列**形态，入口层级没有体现这个定位：

- 顶部导航（`web/src/constant/navigation-tools.ts`）平铺 6 个同级入口：`canvas`（我的画布）、`image`（生图工作台）、`video`（视频创作台）、`prompts`（提示词库）、`assets`（我的资产）、`config`（配置）。画布只是「排在第一个」，结构上与其他工具页完全平级，用户感知是「六个工具里挑一个」，而不是「在一张画布上连续创作」。
- **配置有三处入口指向同一份内容**：`/config` 路由渲染 `AppConfigPanel`（`web/src/pages/config/index.tsx`），顶导航有一个「配置」链接，而顶栏右侧齿轮按钮（`web/src/components/layout/user-status-actions.tsx`）已经通过 `openConfigDialog(false)` 打开同一个面板。
- **素材与提示词在画布里已经有一份**：`CanvasSidePanel` 的三个 tab 就是 `canvas / assets / prompts`（`web/src/components/canvas/canvas-side-panel.tsx`），顶导航又平级暴露 `/assets` 与 `/prompts` 两个整页版本。
- **首页有两个按钮跳同一个地方**：`home.start` 走 `navigationTools[0]`（即 canvas），`home.openCanvas` 直接 `navigate("/canvas")`（`web/src/pages/home/index.tsx`），两个 CTA 目标完全相同。
- 画布内的创建菜单已经能直接创建文本 / 图片 / 视频 / 音频 / 配置节点（`web/src/components/canvas/canvas-create-menus.tsx`），「在画布上创作」的路径本身是通的，缺的只是入口层级。

这些不是视觉问题，而是**信息架构问题**：导航层级没有体现「画布是主工作区，其他是它的能力」。本 brief 只收敛入口层级，不重做画布内部交互、不改视觉风格、不碰生成链路。

必须遵守：

- `docs/engineering/VISION.md`：画布是一等公民。
- `docs/engineering/CONTRIBUTING.md`：只做本 brief 范围。
- `AGENTS.md` 前端规范：文案走 i18n；不给单个组件加全局 CSS；不造空 Manager 组件；全局常量不靠 props 层层透传。
- 不引入 Java API、队列、计费、Anytoken。
- 不改 `web/src/services/api/**` 的生成调用链，不改 `use-config-store` 的供应商与渠道模型。

## 目标

完成后：

1. 顶部导航只保留**一个一级入口「画布」**（画布项目库）；`image` / `video` / `prompts` / `assets` / `config` 五个入口从顶导航移出，改为**画布页内的次级工作台入口**，层级上明确「画布是主工作区，其余是画布的能力」。
2. 五个原路由 `/image`、`/video`、`/prompts`、`/assets`、`/config` **全部保留且可直接访问**，不 404、不跳首页；页面内容与能力不删减。
3. `/config` 不再出现在任何一级导航里；设置面板统一从顶栏右侧齿轮进入（现有行为不变），`/config` 路由作为直接链接的落地页继续可用。
4. 首页收敛为**画布优先落地页**：只保留一个指向画布的主 CTA，消灭两个按钮跳同一路由的重复；提示词展示区保留且仍能到达提示词库。
5. 画布项目库（`/canvas`）顶部提供**次级工作台入口条**，把生图 / 视频 / 提示词库 / 素材库四个入口以图标 + 名称 + 一句话说明的形式呈现，点击直达对应路由。
6. 桌面端顶导航与移动端抽屉的入口层级保持一致；新增文案全部走 `zh-CN` / `en-US` locale，不硬编码中文。
7. 所有新增 / 调整的入口可键盘聚焦、可 Tab 到达、带可读的无障碍名称。

## 范围

允许修改：

- `web/src/constant/navigation-tools.ts`（拆分一级入口与二级工作台入口的常量语义）
- `web/src/components/layout/app-top-nav.tsx`
- `web/src/components/layout/mobile-nav-drawer.tsx`
- `web/src/components/layout/user-status-actions.tsx`（仅在为设置入口补无障碍名称或分组时）
- `web/src/pages/home/index.tsx`
- `web/src/pages/canvas/index.tsx`（加次级工作台入口条）
- `web/src/pages/canvas/components/`（新增画布页私有的工作台入口条组件；按 `AGENTS.md` 放页面私有目录，不要放 `components/canvas/` 共享目录）
- `web/src/i18n/locales/zh-CN.ts`、`web/src/i18n/locales/en-US.ts`（只增改本 brief 涉及的导航 / 工作台 / 首页词条）
- `web/src/router.tsx`（仅在需要时补路由顺序或注释；**不得删除任何路由**）

为满足目标 7，可顺带在上述组件内为既有图标入口补 `aria-label` / `title`。

文档义务（按 `AGENTS.md` 文档规范）：

- `CHANGELOG.md` 的 `Unreleased` 追加一条 `[调整]`。
- `docs/content/docs/progress/pending-test.mdx` 追加一条本版本可测项。
- `docs/content/docs/progress/todo.mdx` 仅当有 todo 状态变化时改；否则确认无需修改。

## 非范围

- **不删任何页面或路由**：`/image`、`/video`、`/prompts`、`/assets`、`/config` 的页面组件保留；`/image`、`/video` 的生成逻辑、批量、历史、素材插入一律不动。
- 不改画布内部交互、节点、右键菜单、侧栏、工具条布局、主题与配色。
- 不改 `web/src/services/api/**`、`web/src/stores/use-config-store.ts`、供应商渠道与调用脚本机制。
- 不做登录 / 账号 / 额度 / 计费 / 队列 / 托管 API 改造（本线只做前端 IA）。
- 不改 `docs/content/**` 里除 `progress/pending-test.mdx`、`progress/todo.mdx` 之外的公开文档页。
- 不重画 logo、不换设计系统、不新增依赖。
- 不动 `canvas-agent/`、`plugins/`、上游包名。
- 不把「画布优先」做成「删掉其它工具」：五个路由必须仍可直达。
- 不新加超时 / 重试 / 限额 / 埋点等默认行为参数（本 brief 不涉及新行为参数）。

## 数据表字段

无。本任务无服务端表，也无浏览器存储结构调整。

## API

无新增、无修改。

## 状态机

无。

## 目标 IA（实现参照）

桌面端顶导航：

```
[flora 标识]   画布                      [Agent] [文档] [设置齿轮] [语言] [主题] [版本] [GitHub]
                ^ 唯一一级入口
```

画布项目库页（`/canvas`）顶部，在标题行下方加一条**次级工作台入口条**：

```
生图 · 批量出图与历史    视频 · 视频创作    提示词库 · 复用提示词    素材库 · 沉淀素材
```

四条都是普通链接（react-router 的 `Link`），不是一堆按钮；顺序建议：生图 → 视频 → 提示词库 → 素材库。

首页：

```
主 CTA（唯一）→ /canvas
副 CTA：查看提示词库 → /prompts    （可保留，但不得与主 CTA 指向同一路由）
下方：提示词展示网格（保留，点击打开预览）
```

移动端抽屉：与桌面端一致，只列一级入口「画布」与右侧操作区等价项；二级工作台入口在 `/canvas` 页内，不重复进抽屉。

## 验收

- [ ] 顶导航在宽度 ≥ `md` 时只渲染一个一级导航链接「画布」；`image` / `video` / `prompts` / `assets` / `config` 不再出现在顶导航链接里。
- [ ] 直接访问 `/image`、`/video`、`/prompts`、`/assets`、`/config` 五个地址，页面正常渲染，不出现 404，也不被重定向到 `/canvas` 或 `/`。
- [ ] `/canvas` 页顶部可见四个次级工作台入口（生图 / 视频 / 提示词库 / 素材库），点击分别直达 `/image`、`/video`、`/prompts`、`/assets`。
- [ ] `/config` 不出现在顶导航；点击顶栏右侧齿轮仍能打开设置面板（与改动前行为一致）；直接访问 `/config` 打开同一份设置面板。
- [ ] 首页只有一个 CTA 指向 `/canvas`，不存在两个按钮指向同一路由的情况；首页提示词展示区仍可点击预览，且仍能到达 `/prompts`。
- [ ] 移动端（< `md`）打开抽屉，一级入口只有「画布」，不出现「生图工作台 / 视频创作台 / 提示词库 / 我的资产 / 配置」这五个平级项。
- [ ] 新增与调整的全部文案在 `zh-CN` 与 `en-US` 两个 locale 中都有对应词条；组件内没有新硬编码的中文字符串（自己 grep 自查）。
- [ ] 切换语言后，新加的工作台入口条、导航项、首页 CTA 文案随之切换。
- [ ] 新增入口均可 Tab 聚焦、有可读的无障碍名称（图标入口必须带 `aria-label`）。
- [ ] 没有修改 `web/src/services/api/**`、`web/src/stores/use-config-store.ts` 的生成与渠道逻辑。
- [ ] 未新增依赖，`package.json` 无变更。
- [ ] `CHANGELOG.md` 的 `Unreleased` 有一条 `[调整]`；`docs/content/docs/progress/pending-test.mdx` 有一条对应的可测项。
- [ ] 用开发服务器人工确认：首页 → `/canvas` → 进入任一画布项目 → 打开侧栏「素材 / 提示词」tab → 回到顶栏齿轮打开设置 → 直接访问 `/image`，全流程无报错、无空白页。

## 非目标

- 不做社区 feed、作品广场、达人体系。
- 不做 admin、不做第二套创作台。
- 不把「画布优先」做成视觉改版：配色、字体、圆角、动效一律不动。
- 不在本任务接托管 API、不做账号与计费。

## 给 Minimax 的额外约束

- 遵守 `docs/engineering/TECH_STACK.md`、`docs/engineering/adr/` 与 `AGENTS.md`；超出本 brief「非范围」的改动一律不做。
- 优先沿用现有结构，不要新增只做转发的组件；页面私有组件放 `web/src/pages/canvas/components/`。
- 入口清单（一级导航、工作台入口）用 `web/src/constant/` 下的常量描述，组件从全局入口直接取，不要靠 props 层层透传。
- 文案一律走 `web/src/i18n/locales/zh-CN.ts` 与 `en-US.ts`，中英同时补齐；不要在组件里写 `if (zh) ... else ...`。
- 画布相关 UI 改动必须沿用当前画布主题（`canvasThemes` / `useThemeStore` / antd token），不要硬编码黑白或 stone/slate 颜色。
- 顶栏保持极简扁平：无边框阴影、无胶囊底色，只保留轻微 hover 反馈。
- 不改无关文件，不重写 `docs/content/` 公开站点。
- 密钥、token 不要写进代码或 PR 描述。
- 本任务不需要新的超时 / 重试 / 限额，禁止顺手加经验值。
- 做完在 PR 描述里逐条打勾上面的「验收」项，并列出**故意没做**的事（生成链路、画布内部、托管 API 等）。
