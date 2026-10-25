# Feature brief：WP0 pilipala 白标

- 日期：2026-10-24
- 作者：pilipala engineering
- 对应 WP / 里程碑：WP0 / M0
- 状态：已实现（待验收）

把本文件从「# Feature brief」起到文末 **整份粘贴** 给 Minimax。不要摘要。实现仓库是 https://github.com/ericlliaocn-hue/pilipala ，品牌是 **pilipala**（小写）。本任务只做白标，不做后端。

## 背景

本仓库是 infinite-canvas 的 fork，产品名改为 **pilipala**。用户打开页面仍看到「无限画布 / Infinite Canvas」、`web/index.html` 标题仍是上游品牌，localforage 与导出清单仍写 `infinite-canvas`。工程文档已在 `docs/engineering/`。WP0 要把 **用户可见品牌** 和 **产品身份字符串** 收成 pilipala，同时保留上游版权与 fork 说明，不把仓库改写成无关项目。

必须遵守：

- `docs/engineering/VISION.md`：对外产品名是 pilipala。
- `docs/engineering/CONTRIBUTING.md`：只做本 brief 范围。
- 不要改 `docs/content/`、`docs/app/`、`docs/Dockerfile`、`docs/source.config.ts` 等公开文档站实现。
- 不要引入 Java API、队列、计费、Anytoken。
- 产品未上线：本地存储 key 可以直接改成 pilipala，**不必**做旧 key 迁移。

## 目标

1. 浏览器标签、落地主文案、i18n 的产品 title/description 显示 pilipala，而不是「无限画布」或「Infinite Canvas」。
2. 前端作为产品身份的字符串（导出 `app` 字段、配置文件 `app` 字段、localforage 库名、主题/locale 的 storage key、WebDAV 默认目录）改为 `pilipala`。
3. `web/package.json` 的 `name` 改为 `pilipala`。
4. 页面图标仍用 `web/public/logo.svg`（可保持文件名）；若 svg 内有必须改的产品字样则改，没有就不要重画一套设计系统。
5. 根 `README.md` **已经** 有 pilipala 说明和 `docs/engineering` 链接的话不要删；没有则在文件最顶部加一段 pilipala 说明，**不要删除** 其后的上游功能/赞助/快速开始。
6. 版本检查若仍指向 `basketikun/infinite-canvas` 的 VERSION/CHANGELOG，改为指向本仓库 `ericlliaocn-hue/pilipala` 的对应 raw 地址，避免用户看到上游版本号当 pilipala 版本。

## 范围

允许修改：

- `web/index.html`（title、description、theme_store key 读取须与主题 store 新 key 一致）
- `web/package.json` 的 `name`（不要为了改名大改依赖）
- `web/src/i18n/locales/zh-CN.ts`、`web/src/i18n/locales/en-US.ts` 中的产品名、默认画布名、导出默认名、落地描述里作为品牌的「无限画布 / Infinite Canvas」
- 产品身份常量与存储：
  - `web/src/i18n/index.ts` 的 locale key
  - `web/src/lib/localforage-storage.ts` 及各 `localforage.createInstance({ name: "infinite-canvas" ... })`
  - 各 `*:canvas_store` / `asset_store` / `ai_config_store` / `theme_store` / `plugin_store` / `prompt_source_store` / 生成日志 key
  - `web/src/services/config-file.ts`、`app-sync.ts`、`canvas-export`、`asset-transfer`、`types/canvas-export.ts` 的 `app: "infinite-canvas"`
  - `web/src/stores/use-config-store.ts` 默认 WebDAV `directory`
  - `web/src/lib/app-theme.ts` 的 cssVar key（`infinite-canvas-dark/light` → `pilipala-dark/light`）
  - `web/src/hooks/use-version-check.ts` 的上游 GitHub raw URL
- 用户可见的「无限画布」四个字作为 **产品名** 出现的组件文案（通过 i18n，不要在组件里新硬编码英文品牌）
- 仅当根 README 缺少 pilipala 段落时编辑 `README.md` 顶部

搜索入口：在 `web/` 内搜 `无限画布`、`Infinite Canvas`、`infinite-canvas`。

## 非范围

- 不改 `docs/content/**`、`docs/app/**`、Fumadocs 站点、文档 Docker。
- 不改 `CHANGELOG.md` 历史条目里的上游项目名（那是历史）。
- 不重写赞助商表格、不删上游作者信息、不把许可证改成另一份。
- 不改 `canvas-agent/`、`plugins/`、Codex 插件包名，除非同一处用户可见字符串把本产品介绍成 Infinite Canvas——若改插件/agent，必须在 PR 里单独列出；**默认不要碰**，避免把 WP0 做成全仓重命名。
- 不改组件名 `InfiniteCanvas`（那是类名，不是品牌）。
- 不改 `npx -y @basketikun/canvas-agent` 等上游包名（包还没白标发布）。
- 不做 API Key 托管、不做 pilipala 业务 API。
- 不新增设计系统、不换主题色「顺便美化」。
- 不添加旧 localforage 数据迁移代码。

## 数据表字段

无。本任务无服务端表。

浏览器存储：

| 存储 | 旧身份 | 新身份 |
| --- | --- | --- |
| localforage `name` | `infinite-canvas` | `pilipala` |
| persist key 前缀 | `infinite-canvas:` | `pilipala:` |
| 导出/配置 JSON `app` | `infinite-canvas` | `pilipala` |
| 配置导出文件名 | `infinite-canvas-config.json` | `pilipala-config.json` |
| WebDAV 默认目录 | `infinite-canvas` | `pilipala` |

读配置/导出时：只认新 `app === "pilipala"`。不要写「旧文件也能导入」的兼容，除非你改到的函数离开兼容会编译失败——即便如此也不要专门加迁移分支。

## API

无后端 API。

版本检查 URL（概念）：

- 旧：`https://raw.githubusercontent.com/basketikun/infinite-canvas/main/VERSION`
- 新：`https://raw.githubusercontent.com/ericlliaocn-hue/pilipala/main/VERSION`
- CHANGELOG 同样改到 `ericlliaocn-hue/pilipala`

`PLUGIN_REGISTRY_URL` 默认仍指向上游 CDN 的话 **本 brief 保持不动**（插件发行尚未白标）。不要改成一个不存在的 pilipala registry。

## 状态机

无。

## 验收

- [ ] `web/index.html` 的 `<title>` 与 description 是 pilipala / pilipala 的中文说明，不再是「无限画布」。
- [ ] zh-CN / en-US 的 `app.title`（或等价产品名键）为 pilipala；默认新画布标题不再叫「无限画布 {{count}}」/「Infinite Canvas {{count}}」，改为 pilipala 或「画布 {{count}}」且品牌不回退到上游产品名。
- [ ] 落地页/关于里作为品牌出现的「无限画布」已替换；操作说明里若只是描述「无限画布」这种品类（一张无限大的画布）可以保留普通名词，但产品名必须是 pilipala。拿不准就改成「画布」。
- [ ] 全仓 `web/src` 内 `localforage.createInstance` 的 `name` 与 persist storage key 不再使用 `infinite-canvas`。
- [ ] 导出画布、导出配置、导出素材、WebDAV manifest 的 `app` 字段写入 `pilipala`，读入时校验 `pilipala`。
- [ ] 打开设置里的 WebDAV 默认目录为 `pilipala`（尚未手动改过的默认值）。
- [ ] 版本检查请求 pilipala 仓库而不是 basketikun/infinite-canvas。
- [ ] `web/package.json` 的 `"name"` 为 `"pilipala"`。
- [ ] 未改 `docs/content/`、未改文档 Dockerfile、未新增后端目录。
- [ ] 根 README 仍能看出这是 infinite-canvas fork，同时顶部能看到 pilipala 与 `docs/engineering`。
- [ ] 用开发服务器打开首页与画布页：标题栏是 pilipala；新建画布名称不含「无限画布」产品名。
- [ ] 不要提交 `node_modules`、lockfile 大范围无关 diff。若 `package.json` name 导致 lock 头字段变化，只保留必要变更。

## 非目标

- 不把 pilipala 做成新的开源社区品牌去替换 Linux.do / 赞助墙。
- 不在本任务发布 npm 包 `@pilipala/canvas-agent`。
- 不做云同步、不做 Java 骨架。
- 管理后台不是本任务。

## 给 Minimax 的额外约束

- 遵守 `docs/engineering/TECH_STACK.md`：本任务只动前端身份，不动栈。
- 遵守 `AGENTS.md` 前端规范：文案走 i18n；不要为单个组件加全局 CSS；不要新造 Manager 空壳组件。
- 先搜再改，列出你将改的文件，然后改；不要全仓 replace `infinite-canvas` 把类名、上游包名、插件命令一起毁掉。
- 密钥、token 不要写进代码。
- 做完后用简短中文列「改了哪些身份、故意没改哪些（agent 包名、插件仓库、公开 docs 站点）」。
- 新的超时/重试/限额：本任务不需要，禁止顺便加。
