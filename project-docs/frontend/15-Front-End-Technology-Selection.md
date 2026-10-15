# 15 — 前端技术选型文档

> 选型日期：2026-06-28 | 决策者：用户确认 | 依赖：02-Architecture（后端架构）、05-API-Specification（接口契约）
> 平台轴扩展：2026-10-15（Web / 桌面端 / Android / iOS / 小程序）| 决策依据：ADR-054（前端多平台化 —— 平台轴与文档结构）
> 归属域: frontend | Owner: frontend-engineer | 更新触发条件: 任一平台技术栈变更时
>
> **本文结构（两级）**：
>
> - **总览（§一–§七）** —— 跨平台共同决策：选型原则、平台矩阵、跨平台统一项、Web 端选型对比、认证语义、契约对接、禁止事项。
> - **平台细则（§八–§十二）** —— 每个平台一套栈与范式；**细节只写在对应平台小节，总览不复制**（单一事实来源）。
> - **§十三 选型总结**。
>
> 平台之间的栈**不互相套用**：Web 与桌面端同属 JS 生态可复用组件与状态管理；Android / iOS 走各自范式；小程序遵循宿主约定。

---

## 一、选型原则

### 1.1 跨平台原则（对所有平台生效）

| 原则             | 说明                                             |
| -------------- | ---------------------------------------------- |
| **面向 2030**    | 技术栈有 5 年以上的生命力，社区活跃，不被单一厂商绑定                   |
| **类型安全**       | TypeScript strict 模式，前后端共享类型契约                 |
| **大厂标准**       | 每个选择有企业验证（React/Next.js 全球主流、Ant Design 阿里出品、Taro 京东出品） |
| **平台轴优先**      | **先定平台 → 再套该平台的栈与范式**。跨生态不得互相套用：把 React 的组件模型搬进 SwiftUI 会写出非惯用代码 |
| **共享物单一来源**    | 设计 token → `17`；API 契约 → `contract/05`；认证语义 → 本文 §五；信息架构 → `product/16`。任一平台不得私自分叉（见 §三） |

### 1.2 Web 端原则（当前唯一已实现平台）

| 原则             | 说明                                             |
| -------------- | ---------------------------------------------- |
| **SSR/SSG 优先** | 电商 SEO 是硬需求，首屏 JS 零负担（React Server Components） |
| **优雅简洁**       | UI 组件可拥有（Shadcn/ui），不依赖黑盒 npm 包                |
| **高并发就绪**      | ISR 静态再生成、CDN 边缘缓存、图片自动优化                      |
| **渐进增强**       | PWA 先行验证；小程序（Taro 4）骨架已建（微信 / 支付宝双宿主）；桌面端与原生 App 待排期 |

---

## 二、平台矩阵

### 2.1 矩阵

> **口径**：语言/框架、构建、包管理三列填的是 `Implementation/front-end` 的**实测值**（读各 `package.json` 与已安装依赖），不是目标值；状态定义见 §2.3。未选型的平台**留空并标注「未决」**，不预填结论。

| 平台 | 生态 | 语言 / 框架 | 构建 | 包管理 | 最低支持版本 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| **Web**（用户端 `web` + 商家端 `seller` + 平台端 `admin`） | JS | TypeScript 5.5 · React 18.2.0（根 `pnpm.overrides` 锁定）· Next.js 15.5.20（App Router） | Turbopack（dev）/ `next build`；pnpm workspace + Turborepo | pnpm 9（`packageManager: pnpm@9.0.0`） | Node ≥ 20（根 `engines`）；**浏览器基线未定义**（待确认项） | ✅ 已实现 |
| **桌面端**（Windows / macOS / Linux） | 未决 | **未选型**（候选：JS 生态 Electron / Tauri） | 未决 | 未决 | 未决 | ⚪ 计划中（选型未决） |
| **Android** | 未决 | **未选型**（候选 A：React Native 0.76，仓库占位 `apps/mobile`；候选 B：Kotlin + Jetpack Compose） | 未决 | 未决 | 未决 | ⚪ 计划中（选型未决） |
| **iOS** | 未决 | **未选型**（候选 A：React Native 0.76，同上；候选 B：Swift + SwiftUI） | 未决 | 未决 | 未决 | ⚪ 计划中（选型未决） |
| **小程序**（微信 / 支付宝） | 小程序 | TypeScript · React 18.2 · Taro 4（`@tarojs/cli` 4.x，已配 `plugin-platform-weapp` + `plugin-platform-alipay`） | `@tarojs/webpack5-runner` | pnpm 9 | 宿主基础库版本**未定义**（待确认项） | 🟡 早期实现（18 个页面骨架，未接契约） |

**两点必须说明**：

1. **`移动端（App）` 不是第六个平台。** 仓库里的 `apps/mobile` 是 React Native 的**可选占位**（其 `README.md` 自述「PWA 覆盖 App 场景，PWA 不够用时激活此项目」，源码为 0 行），它同时服务 Android 与 iOS，不单独成平台。
2. **版本口径以代码为准。** 本文原有正文（§八 各节）声明 React 19 / Vitest / Playwright / ESLint / Husky，与代码实测不一致 —— 差异逐条登记在 §八.1「平台元信息」。**React 19 → 18.2 已于 2026-10-15 裁决并落文档**（fix-doc 批次：口径＝代码为准，故降文档；React 19 升级作为独立议题保留，见 [21-Frontend-Refactor-Readiness](./21-Frontend-Refactor-Readiness.md) §四 决策 4）；Vitest / Playwright / ESLint / Husky 的差异**本轮不改写原声明**（属独立决策）。

### 2.2 各平台选型理由

- **Web —— 电商 SEO 是硬需求。** SSR/ISR/SSG 三种渲染模式按页面切换（§4.1 对比、§8.3.2 渲染决策树）；React Server Components 让商品页零客户端 JS；用户端 / 商家端 / 平台端同在一个 monorepo，共用 `packages/{ui,api,auth,config,utils}`，一套组件与状态管理与三个应用复用。
- **小程序 —— 一套 React 代码编译到双宿主。** Taro 4 已在仓库配置 `weapp` 与 `alipay` 两个平台插件，避免为两个宿主重写一套；代价是宿主的**非 Web 心智**（无 DOM、页面栈路由、样式能力受限），且**双宿主 API 差异必须显式分支**，不假定通用（详见 §十二）。
- **桌面端 —— 暂不选型，因为还没有需求。** PC 形态目前由 Web 后台（`apps/admin`、`apps/seller`）覆盖；只有出现「安装包分发 / 自动更新 / 离线 / 系统级集成」这类 Web 无能力覆盖的需求才立项（详见 §九）。
- **Android / iOS —— 暂不选型，PWA 先行。** `apps/web` 已提供 PWA manifest（`apps/web/public/manifest.json`），先用它覆盖「安装到主屏 / 离线浏览」；React Native 单套代码与 Kotlin+Compose / SwiftUI 原生双端这两条路线的裁决前提见 §十、§十一。

### 2.3 状态口径（判定标准）

| 状态 | 含义 | 判据 |
| --- | --- | --- |
| ✅ 已实现 | 有可运行代码，且已纳入构建 | 目录存在 + 有构建脚本 + 有源码 |
| 🟡 早期实现 | 有源码与构建配置，但未接契约、未联调 | 同上，但 API 层为手写、未对齐 `contract/05` |
| ⚪ 计划中 | 只有占位或完全没有代码 | 无源码，或仅 `README.md` / `package.json` 占位 |

### 2.4 平台细则索引

| 平台 | 细则所在节 | 主要内容 |
| --- | --- | --- |
| Web | §八 | 核心技术栈 · 组件架构 · 认证落地 · 性能 · 测试 · 部署 · 依赖锁定 · AI 交互 |
| 桌面端 | §九 | 待决项与决策前置条件（未选型） |
| Android | §十 | 待决项与决策前置条件（未选型） |
| iOS | §十一 | 待决项与决策前置条件（未选型） |
| 小程序 | §十二 | Taro 4 落地、双宿主差异、现状与差距 |

> 平台细节**不在总览复制**。新增平台时按 §6.2 的骨架补齐对应小节，并同步 §2.1 矩阵的状态。

---

## 三、跨平台统一项（四个共享物）

以下四件东西**必须统一，不得各平台各写一套**。任何一处出现不一致，处理顺序是 **先改上游文档，再改该平台代码** —— 不允许先在代码里分叉、事后再补文档。

| 共享物 | 唯一事实来源 | 各平台落地机制 | 现状 |
| --- | --- | --- | --- |
| **设计 token**（色彩 / 字体 / 间距 / 圆角 / 材质） | 运行时：`Implementation/front-end/apps/web/app/globals.css` 的 `@theme`；镜像：`17` §2.4 | Web：CSS 变量（Tailwind v4 `@theme`）；Android：Compose Theme / `CompositionLocal`；iOS：SwiftUI `Environment` + 资源目录；小程序：样式变量层（`app.scss` 现为事实上的 token 层）；桌面端：随其选型（走 JS 生态可直接复用 CSS 变量） | Web ✅；小程序 🟡（152 处裸 hex，待收敛）；其余未建。映射与允许的偏离登记在 `17` §十二 |
| **API 契约** | `contract/05` | 各平台**只换网络层封装**，路径 / 字段名 / 枚举 / 错误码逐字一致 | Web 🔴 现有 API 层有 8 组路径与后端不符，待按契约重建（[21](./21-Frontend-Refactor-Readiness.md) §三）；小程序 🟡 手写调用未接生成客户端 |
| **认证语义** | 本文 §五 | 语义一致（双 token、刷新、失效）；**存储介质按平台安全能力选**：Web httpOnly Cookie / 小程序 storage / Android Keystore / iOS Keychain / 桌面端系统钥匙串 | 见 §五.2 |
| **信息架构** | `product/16` | 主流程一致；**允许平台特有的导航范式**（iOS Tab Bar、Android 返回手势、小程序无 URL 导航），差异必须登记在 `17` §十二 | 差异待登记（`product/16` 归 product-manager） |

**token 变更的顺序（硬规则）**：新增或修改 token → 先改 `apps/web/app/globals.css` → 再改 `17` §2.4 → 跑 `node Implementation/front-end/scripts/check-design-tokens.mjs`（CI 已接入）。非 Web 平台**不必用 `@theme`**，但必须有一个**与 Web 同源**的 token 模块（同一批数值，不是另一套色板）。

---

## 四、选型对比（为何不选？）

> **范围**：本节对比的是 **Web 端**的框架 / UI / 状态管理选型（当前唯一已实现平台）。其余平台的对比见 §九–§十二。

### 4.1 核心框架对比

| 维度                    | **Next.js 15 ✅** | Nuxt 4          | Remix       | SvelteKit | Astro      |
| --------------------- | ---------------- | --------------- | ----------- | --------- | ---------- |
| **SSR/SSG/ISR**       | 🟢 全部支持          | 🟢 全部支持         | 🟢 SSR only | 🟢 全部支持   | 🟡 SSG 为主  |
| **Server Components** | 🟢 Next.js App Router RSC（**React 18.2 下依赖 Next.js 实现**；React 19 原生 RSC 升级见 §八.1）  | 🟡 无 RSC        | 🟡 无 RSC    | 🟡 无 RSC  | 🟡 Islands |
| **电商 SEO**            | 🟢 ISR 最佳        | 🟢 可用           | 🟢 可用       | 🟢 可用     | 🟢 最佳      |
| **交互密集(购物车/下单)**      | 🟢 混合渲染          | 🟢 SPA 模式       | 🟢 迁移到 SPA  | 🟢 SPA 模式 | 🔴 不适合     |
| **生态规模**              | 🟢 最大            | 🟡 中等           | 🟡 增长中      | 🟡 较小     | 🟡 内容站     |
| **小程序支持**             | 🟡 Taro(React)   | 🟢 uni-app(Vue) | 🟡 无        | 🔴 无      | 🔴 无       |
| **中国公司使用**            | 字节/TikTok/蚂蚁     | 饿了么/美团/B站       | Shopify     | 少数        | 少数         |

**结论**：Next.js 在 SSR/ISR 和交互密集的平衡上最优，React Server Components 是 2026-2030 的方向。

### 4.2 UI 框架对比

| 维度                  | **Shadcn/ui ✅** | Ant Design     | MUI            | Chakra UI      | Headless UI |
| ------------------- | --------------- | -------------- | -------------- | -------------- | ----------- |
| **所有权**             | 🟢 代码属于你        | 🟡 npm 依赖      | 🟡 npm 依赖      | 🟡 npm 依赖      | 🟢 代码属于你    |
| **定制性**             | 🟢 源码直接改        | 🟡 theme token | 🟡 theme token | 🟡 theme token | 🟢 完全自由     |
| **美观度**             | 🟢 现代简约         | 🟡 企业风格        | 🟡 Material    | 🟢 现代简约        | 🟡 无样式      |
| **Table/Form 复杂组件** | 🟡 需自己拼         | 🟢 开箱即用        | 🟡 DataGrid 付费 | 🔴 无           | 🔴 无        |
| **移动端适配**           | 🟢 可适配          | 🔴 PC only     | 🔴 PC only     | 🟡 可适配         | 🟢 可适配      |
| **体积**              | 🟢 按需           | 🟡 较大          | 🔴 大           | 🟡 中等          | 🟢 极小       |

**结论**：两个都用——H5 用户端用 Shadcn/ui（优雅、轻量、移动适配），PC 后台用 Ant Design 5（Table/Form/ProTable 开箱即用）。

### 4.3 状态管理对比

| 维度             | **TanStack Query ✅**  | SWR                 | Redux Toolkit     | Apollo Client      |
| -------------- | --------------------- | ------------------- | ----------------- | ------------------ |
| **缓存策略**       | 🟢 最丰富                | 🟡 够用               | 🔴 需手写            | 🟡 GraphQL only    |
| **乐观更新**       | 🟢 内置                 | 🟡 需配置              | 🟡 需手写            | 🟢 内置              |
| **无限滚动**       | 🟢 `useInfiniteQuery` | 🟢 `useSWRInfinite` | 🔴 需手写            | 🟡 需手写             |
| **DevTools**   | 🟢 专用工具               | 🟡 无                | 🟢 Redux DevTools | 🟡 Apollo DevTools |
| **Next.js 兼容** | 🟢 完美                 | 🟢 完美               | 🟡 需配置            | 🟡 GraphQL 限定      |

### 4.4 对比：为什么不用 Vue？

Vue 生态在中国确实更流行（饿了么/美团/有赞），但：

| 维度                    | React/Next.js           | Vue/Nuxt      |
| --------------------- | ----------------------- | ------------- |
| **Server Components** | ✅ Next.js App Router RSC（React 18.2 + Next.js 15）    | ❌ 无等价物        |
| **ISR 粒度**            | ✅ `revalidate` 60s/page | ⚠️ 有但生态小      |
| **TypeScript 体验**     | ✅ JSX 天然类型推断            | ⚠️ 模板 TS 推断有限 |
| **小程序**               | ⚠️ Taro（React 版）        | ✅ uni-app 更成熟 |
| **全球人才池**             | ✅ 最大                    | ⚠️ 中国为主       |
| **AI 编码辅助**           | ✅ 训练数据最多                | ⚠️ 较少         |

小程序方面的劣势由 Taro 4 弥补——京东的 Taro 在 React 生态小程序编译方面已经足够成熟。

---

## 五、认证方案（跨平台语义）

### 5.1 JWT 双 Token 流转

```
┌──────────┐       ┌──────────┐       ┌──────────────┐
│  Browser  │       │ Next.js   │       │ auth-service  │
│  (Client) │       │ (BFF/MW)  │       │  (Java)       │
└─────┬─────┘       └─────┬─────┘       └──────┬───────┘
      │                   │                     │
      │  POST /api/auth/login (phone+code)      │
      │──────────────────▶│─────────────────────▶
      │                   │                     │
      │                   │  access_token(JWT)  │
      │                   │  refresh_token      │
      │                   │◀────────────────────│
      │                   │                     │
      │  Set-Cookie:      │                     │
      │  access_token     │                     │
      │  (httpOnly,secure)│                     │
      │◀──────────────────│                     │
      │                   │                     │
      │  后续请求自动带 Cookie                   │
      │──────────────────▶│                     │
      │                   │  Middleware:         │
      │                   │  验证 JWT (签名+过期) │
      │                   │  → 通过: 附加 headers  │
      │                   │  → 过期: 用 refresh 换 │
      │                   │  → 失败: 302 /auth/login│
      │                   │─────────────────────▶│
```

**跨平台语义（不变）**：access token 短期、refresh token 长期、过期用 refresh 换新、刷新失败清凭据并跳登录。**唯一随平台变化的是存储介质**（见 §5.2）——语义若在各平台不同，就是缺陷，不是适配。

### 5.2 各平台 token 存储介质

| 平台 | 介质 | 选择理由 | 现状 |
| --- | --- | --- | --- |
| **Web** | httpOnly `access_token` Cookie（`Secure`、`SameSite=Lax`、max-age 1800） | 阻断 XSS 读取 | 🔴 `refresh_token` 目前仍在 **JS 可读 Cookie**（7 天），[21](./21-Frontend-Refactor-Readiness.md) §四 决策 1 建议改 httpOnly + BFF/Route Handler 代理刷新，**待落地** |
| **小程序** | 宿主 `storage`（`Taro.setStorageSync`） | 宿主环境无 Cookie 语义 | 🟡 现状（未接契约） |
| **Android** | Keystore 保护的 `EncryptedSharedPreferences` | 系统级密钥保护 | ⚪ 未建（平台选型未决） |
| **iOS** | Keychain | 同上 | ⚪ 未建（平台选型未决） |
| **桌面端** | 系统钥匙串（Keychain / Credential Manager / libsecret） | Web 的 httpOnly Cookie 方案在桌面壳内不成立（壳内请求可被脚本读取） | ⚪ 未建（平台选型未决） |

> 具体实现（Next.js Middleware 路由守卫）属 Web 平台细则，见 §8.4。

---

## 六、与后端契约对接

### 6.1 API 协议

| 维度               | 约定                                                                                    |
| ---------------- | ------------------------------------------------------------------------------------- |
| **Base URL**     | `https://api.icedmall.com` (外部) / `http://gate-service:8080` (内部)                     |
| **Content-Type** | `application/json; charset=utf-8`                                                     |
| **Auth**         | Cookie: `access_token` (httpOnly, Secure, SameSite=Lax, max-age=1800)                 |
| **响应格式**         | `{ "code": 200, "msg": "success", "data": T }` (参考 `ia-common Result<T>`)             |
| **错误格式**         | `{ "code": 40001, "msg": "手机号已注册", "data": null }`                                    |
| **分页格式**         | `{ "code": 200, "data": { "records": T[], "total": 100, "size": 20, "current": 1 } }` |

### 6.2 类型生成

从 `ia-api` 模块的 Knife4j OpenAPI 规范生成 TypeScript 类型：

```bash
# 从后端 OpenAPI JSON 生成 TypeScript 类型
npx openapi-typescript http://localhost:8080/v3/api-docs -o packages/api/types/schema.ts
```

这确保前后端类型契约永不漂移。

> **多平台含义**：生成产物是**全平台共用**的类型与客户端（Web / 小程序 / 未来的原生端都从这里出），各平台只在网络层做封装。生成方案本身待裁决，见 [21](./21-Frontend-Refactor-Readiness.md) §四 决策 2。

---

## 七、禁止事项

**跨平台通用禁止项**（对所有平台生效）：

| ❌ 禁止 | ✅ 替代 |
| --- | --- |
| 硬编码色值 / 间距 / 圆角（裸 hex、魔数 px） | 从 `17` 的 token 注入；确有例外时在该行写 `design-token-exempt: <理由>` |
| 私自偏离 `contract/05` 的路径 / 字段 / 枚举 / 错误码 | 契约缺陷走「变更交接摘要」报出，不就地兜底 |
| 各平台各写一套 token / 认证语义 | 先改上游文档（`17` / `contract/05`），再改代码 |
| 把别的生态的写法搬过来（React 组件模型搬进 SwiftUI、Web 路由搬进小程序） | 用该平台的惯用范式 |

**Web 端禁止项**：

| ❌ 禁止                             | ✅ 替代                                 |
| -------------------------------- | ------------------------------------ |
| Redux (模板太多)                     | Zustand + TanStack Query             |
| CSS-in-JS 运行时 (RSC 不兼容)          | Tailwind CSS + CSS Modules           |
| class 组件                         | React 函数组件 + Hooks                   |
| `any` 类型                         | 严格 TypeScript + Zod 推断               |
| 直接操作 localStorage Token          | httpOnly Cookie                      |
| Webpack 自定义配置                    | Turbopack / 零配置                      |
| npm/yarn                         | pnpm (Monorepo 最佳)                   |
| Axios (非必需)                      | 原生 `fetch` + TanStack Query (RSC 兼容) |
| 在 Client Component 中 `fetch` 数据库 | Server Actions / API Routes          |
| 图片用 `<img>`                      | `next/image` (自动优化)                  |

---

## 八、Web 平台细则（✅ 已实现）

### 8.1 平台元信息（实测）

| 项 | 实测值 | 出处 |
| --- | --- | --- |
| 应用目录 | `apps/web`（用户端）· `apps/seller`（商家后台）· `apps/admin`（平台后台） | `Implementation/front-end/apps/` |
| 框架 | Next.js 15.5.20（App Router） | 各 app 已安装 `next` 版本 |
| UI 库 | React 18.2.0（根 `pnpm.overrides` 强制锁定） | 根 `package.json` → `pnpm.overrides` |
| 类型系统 | TypeScript 5.5（已安装 5.9.3） | 根 `devDependencies` |
| 样式 | Tailwind CSS v4（CSS-first `@theme`）+ `lucide-react` | `apps/web/package.json` |
| 后台 UI | `antd` 5 + `@ant-design/pro-components` | `apps/admin`、`apps/seller/package.json` |
| 状态 | `@tanstack/react-query` 5.62.0（服务端）+ `zustand`（客户端） | 各 app `package.json` |
| 表单 / 校验 | `react-hook-form` + `zod` | 各 app `package.json` |
| 共享包 | `packages/{ui,api,auth,config,utils}` | `pnpm-workspace.yaml` |
| 构建 / 包管理 | Turborepo + Turbopack；pnpm 9.0.0；Node ≥ 20 | 根 `package.json` |
| PWA | `apps/web/public/manifest.json` 存在；Service Worker **未见独立文件**（未核实） | `apps/web/public/` |

**漂移登记**（本文原有声明与代码不一致。**本轮的处置是登记，不是静默改写** —— 改写属独立决策）：

| 本文原声明 | 代码实测 | 处置 |
| --- | --- | --- |
| React 19（§2.2、§12、§九） | **18.2.0**（各 `apps/*/package.json` 为 `~18.2.0`，根 `pnpm.overrides` 强制锁定 `18.2.0`） | ✅ **已裁决（2026-10-15 fix-doc）：降文档为 React 18.2**（口径＝版本以代码为准）。本批次已把「React 19」表述逐条更正为 18.2（§2.2/:112、§八 骨架 :300、§十二/:951、§十二 流式渲染注记），并在 :57 声明处标明**决策已闭环**。**React 19 升级作为独立议题保留**（不在本批次）：前置条件见 [21](./21-Frontend-Refactor-Readiness.md) §四 决策 4 —— 需先落实 RSC/`useOptimistic` 等 19-only 能力的收益评估与全端回归 |
| Vitest / RTL / Playwright / MSW（§7.1、§九） | **均未安装**（无任何 `package.json` 声明） | 测试策略是**规格**而非现状；落地见 `18` 与 `quality/11` |
| ESLint 9 / Husky / lint-staged（§2.2、§九） | **均未安装**（仅根 `prettier` + `turbo`） | 同上 |
| 目录名 `apps/storefront`（§4.1） | 实际为 **`apps/web`** | 待同步（与 `18` 目录结构一并处理） |

### 8.2 核心技术栈

#### 8.2.1 总览

```
┌──────────────────────────────────────────────────────────────────┐
│                    冰美商城 前端技术栈 (2026)                       │
├──────────────────────────────────────────────────────────────────┤
│  框架      Next.js 15+ (App Router) + React 18.2 + TypeScript 5.x  │
│  样式      Tailwind CSS 4 + Shadcn/ui + Ant Design 5 (后台)       │
│  状态      TanStack Query v5 (服务端) + Zustand v5 (客户端)        │
│  表单      React Hook Form + Zod                                   │
│  测试      Vitest + Testing Library + Playwright (E2E)            │
│  构建      Turbopack + pnpm monorepo                               │
│  移动      PWA (Service Worker) → Taro 4 (小程序，后期)            │
│  监控      Core Web Vitals + Sentry                                │
└──────────────────────────────────────────────────────────────────┘
```

#### 8.2.2 详细技术清单

**核心框架**

| 技术             | 版本  | 角色   | 选型理由                                                                                  |
| -------------- | --- | ---- | ------------------------------------------------------------------------------------- |
| **Next.js**    | 15+ | 全栈框架 | App Router 成熟稳定，React Server Components 默认开启，ISR/SSG/SSR 三种渲染模式按需切换。Vercel 持续投入，社区第一。 |
| **React**      | 19  | UI 库 | Server Components 革命性创新：商品页零客户端 JS 渲染。`use()` hook、actions、Suspense 流式渲染。全球最大生态。      |
| **TypeScript** | 5.x | 类型系统 | strict 模式。前后端共享 DTO 类型定义（从 `ia-api` 的 OpenAPI 生成）。                                    |

**样式 & 组件**

| 技术               | 版本     | 角色      | 选型理由                                                              |
| ---------------- | ------ | ------- | ----------------------------------------------------------------- |
| **Tailwind CSS** | 4      | 原子化 CSS | 设计系统约束在 design tokens，不会产生废弃 CSS。v4 的 `@theme` + CSS-first 配置更简洁。 |
| **Shadcn/ui**    | latest | 通用 UI   | 组件代码直接复制到项目中（非 npm 依赖），完全可控可定制。基于 Radix 原语，无障碍访问内置。移动端适配良好。       |
| **Ant Design**   | 5      | 后台 UI   | 阿里出品，Table/Form/ProTable 开箱即用，企业级后台事实标准。仅用于商家后台和平台管理后台。           |
| **Lucide**       | latest | 图标      | 轻量 SVG 图标，与 Shadcn/ui 配套。                                         |

**状态管理**

| 技术                 | 版本  | 角色    | 选型理由                                                                                               |
| ------------------ | --- | ----- | -------------------------------------------------------------------------------------------------- |
| **TanStack Query** | v5  | 服务端状态 | 请求去重、缓存失效、乐观更新、无限滚动、分页——一站式解决。比 SWR 更灵活。Next.js App Router 完美配合（Server Components 预取 + Client 水合）。 |
| **Zustand**        | v5  | 客户端状态 | 购物车草稿、UI 状态、认证 Token。API 极简（无 Provider/Reducer 模板），TS 推断完美。                                        |

**表单 & 校验**

| 技术                  | 版本  | 角色        | 选型理由                                                     |
| ------------------- | --- | --------- | -------------------------------------------------------- |
| **React Hook Form** | 7.x | 表单管理      | 非受控组件，性能最优（不触发全树重渲染）。与 Zod 集成无缝。                         |
| **Zod**             | 3.x | Schema 校验 | 类型推断 + 运行时校验合一。前后端共享校验规则（从 Zod schema 生成 TypeScript 类型）。 |

**测试**

| 技术                        | 版本     | 角色       | 选型理由                                                   |
| ------------------------- | ------ | -------- | ------------------------------------------------------ |
| **Vitest**                | latest | 单元/组件测试  | Vite 原生速度。与 Jest API 兼容，迁移成本低。                         |
| **React Testing Library** | latest | 组件测试     | 以用户视角测试（不测实现细节）。Next.js 官方推荐。                          |
| **Playwright**            | latest | E2E 测试   | 多浏览器并行，trace viewer 调试，API mocking 内置。比 Cypress 更快更稳定。 |
| **MSW**                   | 2.x    | API Mock | Service Worker 级别的请求拦截，测试和开发共用 mock handler。           |

**构建 & 工程化**

| 技术                      | 版本     | 角色        | 选型理由                                                       |
| ----------------------- | ------ | --------- | ---------------------------------------------------------- |
| **Turbopack**           | —      | 开发构建      | Next.js 内置，Rust 实现，比 Webpack 快 10x。                        |
| **pnpm**                | 9+     | 包管理       | 严格依赖解析、磁盘高效、monorepo workspace 原生支持。                       |
| **ESLint**              | 9      | 代码检查      | Flat config 模式。`eslint-config-next` + `typescript-eslint`。 |
| **Prettier**            | 3      | 代码格式化     | 配合 `prettier-plugin-tailwindcss` 自动排序 class。               |
| **Husky + lint-staged** | latest | Git hooks | 提交前自动 lint + format。                                       |

**移动端 & 小程序**

| 技术       | 版本  | 角色          | 选型理由                                                                                    |
| -------- | --- | ----------- | --------------------------------------------------------------------------------------- |
| **PWA**  | —   | 渐进式 Web App | Service Worker 离线缓存、安装到桌面、推送通知。Next.js 内置 `next-pwa` 或手动配 Workbox。先以 PWA 验证业务，降低初期开发成本。 |
| **Taro** | 4.x | 跨端小程序       | 京东出品。React 语法编写，一套代码编译到微信/支付宝/抖音小程序 + H5。待 PWA 验证 PMF 后再补入。                             |

**监控 & 分析**

| 技术                     | 版本     | 角色   | 选型理由                                               |
| ---------------------- | ------ | ---- | -------------------------------------------------- |
| **Sentry**             | latest | 错误追踪 | React Error Boundary 集成，源码映射，Session Replay。       |
| **Web Vitals**         | —      | 性能指标 | LCP/FID(INP)/CLS 上报。`@vercel/speed-insights` 开箱即用。 |
| **Google Analytics 4** | —      | 用户分析 | 电商漏斗分析（浏览→加购→下单→支付转化率）。                            |

### 8.3 组件架构

#### 8.3.1 项目结构（Monorepo）

```
front-end/
├── apps/
│   ├── storefront/          # 用户端 (H5 + PWA)
│   │   ├── app/             # Next.js App Router
│   │   │   ├── (marketing)/ # 营销页面组 (SSG/ISR)
│   │   │   │   ├── page.tsx           # 首页
│   │   │   │   ├── products/
│   │   │   │   │   ├── page.tsx       # 商品列表 (ISR 60s)
│   │   │   │   │   └── [id]/
│   │   │   │   │       └── page.tsx   # 商品详情 (ISR 300s)
│   │   │   │   └── categories/
│   │   │   │       └── [slug]/page.tsx
│   │   │   ├── (shop)/      # 交易页面组 (SSR + Client)
│   │   │   │   ├── cart/page.tsx       # 购物车 (Client)
│   │   │   │   ├── checkout/page.tsx   # 下单 (Client + SSR)
│   │   │   │   ├── orders/
│   │   │   │   │   ├── page.tsx        # 订单列表
│   │   │   │   │   └── [id]/page.tsx   # 订单详情
│   │   │   │   └── payment/
│   │   │   │       └── [id]/page.tsx
│   │   │   ├── (user)/      # 用户页面组
│   │   │   │   ├── profile/page.tsx
│   │   │   │   ├── addresses/page.tsx
│   │   │   │   └── sign-in/page.tsx    # 签到
│   │   │   ├── auth/
│   │   │   │   ├── login/page.tsx
│   │   │   │   └── register/page.tsx
│   │   │   ├── layout.tsx    # 根布局
│   │   │   └── globals.css
│   │   └── public/
│   │
│   ├── admin/                # 平台管理后台 (PC, SPA 模式)
│   │   ├── app/
│   │   │   ├── layout.tsx    # Ant Design ProLayout
│   │   │   ├── dashboard/
│   │   │   ├── users/
│   │   │   ├── products/
│   │   │   ├── orders/
│   │   │   ├── finance/
│   │   │   └── settings/
│   │   └── public/
│   │
│   └── seller/               # 商家后台 (PC, SPA 模式)
│       ├── app/
│       │   ├── layout.tsx    # Ant Design ProLayout
│       │   ├── dashboard/
│       │   ├── products/
│       │   ├── orders/
│       │   ├── marketing/
│       │   ├── finance/
│       │   └── settings/
│       └── public/
│
├── packages/
│   ├── ui/                   # 共享 UI 组件 (Shadcn/ui + 自定义)
│   │   ├── components/
│   │   │   ├── product-card.tsx
│   │   │   ├── product-grid.tsx
│   │   │   ├── cart-drawer.tsx
│   │   │   ├── search-bar.tsx
│   │   │   ├── user-menu.tsx
│   │   │   └── ...
│   │   └── lib/
│   │       └── utils.ts      # Shadcn/ui 工具函数
│   │
│   ├── api/                  # API 客户端 + 类型定义
│   │   ├── client.ts         # Fetch wrapper (base URL, JWT interceptor)
│   │   ├── types/            # TypeScript 类型 (从 ia-api 的 OpenAPI 生成)
│   │   │   ├── user.ts
│   │   │   ├── item.ts
│   │   │   ├── order.ts
│   │   │   └── ...
│   │   ├── hooks/            # TanStack Query hooks
│   │   │   ├── use-products.ts
│   │   │   ├── use-cart.ts
│   │   │   ├── use-orders.ts
│   │   │   ├── use-auth.ts
│   │   │   └── ...
│   │   └── queries/          # Query key 工厂
│   │
│   ├── auth/                 # 认证模块
│   │   ├── jwt.ts            # JWT 解析 + 刷新
│   │   ├── middleware.ts     # Next.js middleware (路由守卫)
│   │   └── store.ts          # Zustand auth store
│   │
│   ├── config/               # 共享配置
│   │   ├── tailwind.config.ts
│   │   ├── eslint.config.ts
│   │   └── tsconfig.base.json
│   │
│   └── utils/                # 工具函数
│       ├── format.ts         # 金额/日期格式化
│       ├── validate.ts       # Zod schema 库
│       └── constants.ts
│
├── pnpm-workspace.yaml
├── package.json
├── turbo.json                # Turborepo pipeline
└── .github/
    └── workflows/
        └── frontend-ci.yml
```

> **注意**：上方目录树里的 `apps/storefront` 是**原设计名**，实际目录为 `apps/web`（见 §8.1 漂移登记）；`packages/config/tailwind.config.ts` 与各 app 的 `tailwind.config.ts` 已随 Tailwind v4 的 CSS-first 迁移删除（`check-design-tokens.mjs` R1 守卫此项）。

#### 8.3.2 渲染策略决策树

```
                        ┌─ 需要 SEO？ ────┐
                        │                 │
                        YES               NO
                        │                 │
                  内容变化频率？     需要实时交互？
                        │                 │
              ┌────────┼────────┐    ┌────┼────┐
              │        │        │    │         │
            静态     分钟级    实时  YES       NO
              │        │        │    │         │
           SSG      ISR      SSR   Client    SSG
              │        │        │    │         │
          首页内容  商品列表  商品详情 购物车  静态页面
          帮助中心  分类页   订单详情 下单页  营销落地页
```

#### 8.3.3 页面 → 渲染模式映射

| 页面      | 渲染模式                      | 理由                        |
| ------- | ------------------------- | ------------------------- |
| 首页      | SSG + ISR 60s             | 内容相对稳定，需要秒开 + SEO         |
| 商品列表    | ISR 60s                   | 商品变化中等，需要 SEO + 搜索友好      |
| 商品详情    | ISR 300s + Client islands | 基本信息稳定，库存/价格 client fetch |
| 分类页     | SSG                       | 类目结构变化极少                  |
| 购物车     | Client Component          | 纯交互，无需 SEO                |
| 下单/支付   | Client Component          | 表单密集，需要实时校验               |
| 订单列表/详情 | SSR (auth required)       | 需要登录，但不能缓存                |
| 登录/注册   | Client Component          | 表单交互                      |
| 商家后台    | SPA (Client only)         | 纯管理功能，无需 SEO              |
| 平台管理后台  | SPA (Client only)         | 纯管理功能，无需 SEO              |

### 8.4 认证落地（Web）

**中间件路由守卫（Next.js Middleware）**：

```typescript
// packages/auth/middleware.ts
export const config = {
  matcher: ['/cart/:path*', '/checkout/:path*', '/orders/:path*',
            '/profile/:path*', '/seller/:path*', '/admin/:path*']
}

export async function middleware(request: NextRequest) {
  const accessToken = request.cookies.get('access_token')?.value
  const refreshToken = request.cookies.get('refresh_token')?.value

  if (!accessToken && !refreshToken) {
    return NextResponse.redirect(new URL('/auth/login', request.url))
  }

  // 验证 access token (RS256 公钥)
  const payload = await verifyJWT(accessToken)
  if (payload) {
    // 通过，附加 userId/username 到 headers
    return withUserHeaders(request, payload)
  }

  // Access token 过期 → 尝试 refresh
  const newTokens = await refreshAccessToken(refreshToken)
  if (!newTokens) {
    return clearCookiesAndRedirect(request)
  }

  return withNewTokens(request, newTokens)
}
```

**现状（实测）**：`packages/api/token.ts` 自注存在 XSS 暴露面；`refresh_token` 仍是 **JS 可读 Cookie（7 天）**，与本方案 §5.2 的介质选择不一致。改法（httpOnly refresh + BFF 代理刷新）见 [21](./21-Frontend-Refactor-Readiness.md) §四 决策 1，**待落地**。

### 8.5 性能策略

#### 8.5.1 高并发场景设计

```
秒杀/大促流量路径：

用户请求
    │
    ▼
CDN (商品图片+静态资源) ──── 缓存命中 → 直接返回
    │
    ▼
Next.js (ISR/SSG 页面) ──── 页面缓存 → 不触及后端
    │
    ▼
Gateway (Spring Cloud Gateway) ──── 限流/熔断 (Sentinel)
    │
    ▼
后端微服务集群
```

| 层级         | 优化手段                                                        | 目标            |
| ---------- | ----------------------------------------------------------- | ------------- |
| **静态资源**   | CDN + immutable cache + 图片 WebP/AVIF 自动转换                   | 图片 LCP < 2.5s |
| **页面渲染**   | ISR（商品列表/详情）+ SSG（首页/分类）                                    | TTFB < 200ms  |
| **API 缓存** | TanStack Query 客户端缓存 + `staleTime` 配置                       | 减少 70% 重复请求   |
| **代码分割**   | Next.js Route-based splitting + `dynamic(() => import())`   | 首屏 JS < 100KB |
| **图片优化**   | `next/image` (自动 WebP/AVIF, lazy loading, blur placeholder) | 图片体积减少 60%    |
| **字体**     | `next/font` (自托管 Google Fonts, 无外部请求)                       | FOIT 0ms      |
| **预取**     | `<Link prefetch>` 视口内链接自动预取                                 | 页面切换 < 100ms  |
| **PWA**    | Service Worker 离线缓存 + Cache-Control: stale-while-revalidate | 二次访问秒开        |

#### 8.5.2 Core Web Vitals 目标

| 指标                | 目标      | 说明             |
| ----------------- | ------- | -------------- |
| **LCP** (最大内容绘制)  | < 2.5s  | 商品首图、轮播 Banner |
| **INP** (交互到下次绘制) | < 200ms | 加购按钮、结算流程      |
| **CLS** (累计布局偏移)  | < 0.1   | 防止图片加载导致的布局跳动  |

### 8.6 测试策略

#### 8.6.1 测试分布（参考 11-Test-Strategy）

| 层级       | 工具                | 覆盖目标                 | 示例                            |
| -------- | ----------------- | -------------------- | ----------------------------- |
| **单元测试** | Vitest            | 工具函数、Zod schema、状态管理 | `formatPrice(1250) → "12.50"` |
| **组件测试** | Vitest + RTL      | UI 交互、表单校验、边界状态      | 购物车数量加减、表单错误提示                |
| **集成测试** | Playwright + MSW  | 关键业务流程的端到端           | 浏览商品→加购→下单→支付成功               |
| **视觉回归** | Percy / Chromatic | 组件视觉一致性              | 按钮/卡片/表单样式变更检测                |
| **性能测试** | Lighthouse CI     | PR 级别性能门禁            | LCP > 3s 阻止合并                 |

> **现状**：以上工具链**尚未安装**（见 §8.1 漂移登记）。本节是**规格**，落地状态以 `18` 与 `quality/11` 为准。

#### 8.6.2 关键 E2E 场景

```
场景 1: 未登录用户浏览商品
  Given 用户访问首页
  When 用户点击分类 → 浏览商品列表 → 点击商品 → 查看详情
  Then 所有页面 ISR/SSG 渲染，LCP < 2.5s

场景 2: 用户下单流程
  Given 用户已登录，购物车有 2 件商品
  When 用户进入购物车 → 点击结算 → 填写地址 → 提交订单 → 微信支付
  Then 订单创建成功，页面跳转到订单详情

场景 3: 商家管理商品
  Given 商家已登录
  When 商家进入后台 → 新增商品 → 添加 SKU → 设置库存 → 上架
  Then 商品出现在用户端商品列表中

场景 4: 高并发秒杀
  Given 1000 用户同时访问秒杀页面
  When 库存 100 件的商品开放购买
  Then 前 100 个请求成功，第 101 个看到"已售罄"
```

### 8.7 部署架构

```
                          ┌──────────────┐
                          │   CDN        │
                          │ (static/img) │
                          └──────┬───────┘
                                 │
                          ┌──────▼───────┐
                          │    Nginx     │  TLS 终结 + 反向代理
                          └──────┬───────┘
                                 │
                    ┌────────────┼────────────┐
                    │            │            │
              ┌─────▼─────┐ ┌───▼────┐ ┌────▼─────┐
              │ Next.js    │ │ Next.js│ │ Next.js  │
              │ Server 1   │ │Server 2│ │ Server N │  (Docker 集群)
              └─────┬──────┘ └───┬────┘ └────┬─────┘
                    │            │            │
                    └────────────┼────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │  Spring Cloud Gateway   │
                    └────────────┬────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │  Backend Microservices  │
                    └─────────────────────────┘
```

| 组件              | 部署方式                     | 说明                             |
| --------------- | ------------------------ | ------------------------------ |
| **静态资源**        | CDN (阿里云 CDN/Cloudflare) | `_next/static/` + `public/` 目录 |
| **Next.js App** | Docker 容器 (K8s/Compose)  | `next start` 生产模式，多实例 + 负载均衡   |
| **ISR 缓存**      | 共享 Redis / 文件系统          | 多实例间 ISR 缓存一致性                 |
| **图片优化**        | `next/image` + 独立图片服务    | 生产环境可替换为阿里云图片处理                |

### 8.8 依赖版本锁定

> **⚠️ 版本口径（2026-10-15 更正）**：下例中的 `react`/`react-dom` 应为 **`~18.2.0`**（各 `apps/*/package.json` 实测），且根 `pnpm.overrides` **强制锁定 `18.2.0`**（含 `@types/react` 与 `@types/react-dom`）——**「^19.0.0」为过时表述，已更正**。React 19 升级为独立议题（§八.1）。

```json
{
  "dependencies": {
    "next": "^15.0.0",
    "react": "~18.2.0",
    "react-dom": "~18.2.0",
    "typescript": "^5.5.0",
    "tailwindcss": "^4.0.0",
    "@tanstack/react-query": "^5.0.0",
    "zustand": "^5.0.0",
    "react-hook-form": "^7.50.0",
    "zod": "^3.23.0",
    "antd": "^5.20.0",
    "@ant-design/pro-components": "^2.10.0",
    "lucide-react": "^0.400.0",
    "next-themes": "^0.3.0"
  },
  "devDependencies": {
    "vitest": "^2.0.0",
    "@testing-library/react": "^16.0.0",
    "@playwright/test": "^1.45.0",
    "msw": "^2.3.0",
    "eslint": "^9.0.0",
    "prettier": "^3.3.0",
    "prettier-plugin-tailwindcss": "^0.6.0",
    "husky": "^9.0.0",
    "lint-staged": "^15.0.0",
    "turbo": "^2.0.0"
  }
}
```

> **现状**：根 `package.json` 额外通过 `pnpm.overrides` 锁定 `react` / `react-dom` **18.2.0** 与 `@tanstack/react-query` 5.62.0；`vitest` / `@playwright/test` / `msw` / `eslint` / `husky` / `lint-staged` **均未安装**（见 §8.1）。

### 8.9 AI 交互前端 (V2.5+)

> AI Chat UI 为 V2.5 新增。后端 AI 功能（ai-service）已有 V2.0 MVP 实现，前端需补齐对应的交互组件。

#### 8.9.1 AI Chat 组件选型

| 方案                                              | 适用场景      | 优势                                                                    |
| ----------------------------------------------- | --------- | --------------------------------------------------------------------- |
| **Vercel AI SDK** (`@ai-sdk/react` + `useChat`) | 🟢 **首选** | `useChat` hook 一行代码接入 SSE 流式对话；自动处理 loading/error/重连；支持 tool-call 可视化 |
| **SSE EventSource + 自建 UI**                     | 需完全自定义    | 无框架依赖，UI 完全自主                                                         |
| **shadcn-chat**                                 | 快速原型      | 基于 shadcn/ui 的现成 Chat 组件，与项目 UI 体系一致                                  |

**推荐组合：Vercel AI SDK (`useChat`) + shadcn-chat 基础样式 + 自定义业务组件。**

```tsx
// 示例：使用 Vercel AI SDK 接入 AI 商品助手
'use client';
import { useChat } from '@ai-sdk/react';

export function ShoppingAssistant() {
  const { messages, input, handleInputChange, handleSubmit, isLoading } = useChat({
    api: '/api/ai/chat',  // Next.js API Route → 代理到 ai-service
    streamProtocol: 'text',
  });

  return (
    <div className="chat-container">
      {messages.map(m => (
        <div key={m.id} className={`message ${m.role}`}>
          {m.content}
          {m.toolInvocations?.map(tool => (
            <ToolCallCard key={tool.toolName} {...tool} />
          ))}
        </div>
      ))}
      {isLoading && <TypingIndicator />}
      <form onSubmit={handleSubmit}>
        <input value={input} onChange={handleInputChange} placeholder="问我任何关于商品的问题..." />
        <button type="submit">发送</button>
      </form>
    </div>
  );
}
```

#### 8.9.2 架构模式

```
浏览器 (H5)
    │
    ├── POST /api/ai/chat (非流式, V2.0)
    │   └── 传统 fetch → JSON response
    │
    └── POST /api/ai/chat/stream (流式, V2.5)
        └── EventSource / fetch + ReadableStream
            └── SSE: data: {"token": "好的"} ...
```

**关键决策**：

- **Next.js API Route 作为 BFF**：前端请求先到 Next.js server，由 server 转发到 ai-service，避免 CORS + 隐藏 API Key
- **流式消费用 `useChat`**：Vercel AI SDK 自动处理 `text/event-stream` 解析、重连、状态同步
- **会话管理**：`conversationId` 存储在 Zustand store，首次对话自动创建，后续请求携带

#### 8.9.3 交互设计规范

| 状态           | UI 表现                              | 说明                         |
| ------------ | ---------------------------------- | -------------------------- |
| **空闲**       | 输入框 + 欢迎语 + 快捷问题推荐                 | 引导用户开始对话                   |
| **等待中**      | 发送按钮变 loading + 消息列表末尾显示骨架屏        | 用户发送后，等待首个 token 返回        |
| **流式输出中**    | 打字机效果 + 光标闪烁                       | SSE 逐 token 渲染             |
| **Tool 调用中** | 内联提示："正在搜索商品..." → 完成后展开 Tool 结果卡片 | 透明度提示 AI 在做什么              |
| **错误**       | Toast 提示 + 重试按钮                    | 区分：网络错误/超时/AI未启用/限流        |
| **完成**       | 完整回复 + 👍👎反馈按钮                    | 收集用户反馈                     |
| **AI 未启用**   | 静态提示："AI 助手暂未开启"                   | `ai.enabled=false` 时的降级 UI |

#### 8.9.4 性能注意事项

- **流式渲染**：`useChat` 返回的 messages 增量更新，避免整列表重渲染（**注**：React 19 的 `useOptimistic` 在 React 18.2 下**不可用**——乐观更新需自行实现，或随 React 19 升级一并启用，见 §八.1）
- **长对话虚拟化**：超过 50 条消息时，使用 `react-virtuoso` 虚拟滚动（类似 ChatGPT）
- **首次加载**：AI Chat 组件懒加载（`next/dynamic` + `ssr: false`），不影响首屏
- **防抖输入**：用户在快速输入时不重复触发（`useChat` 内置 debounce）
- **Token 计数**：前端本地估算显示（`input.length * 2 ≈ tokens`），帮助用户了解用量

> **平台范围**：本节实现全部基于 Next.js（API Route BFF + `useChat`），**当前仅 Web 可用**。其他平台若要接同一后端 `/api/ai/chat/stream`，需各写一份流式消费实现（OpenAI 兼容 SSE），并在 `17` §十二 登记交互差异。

---

## 九、桌面端平台细则（⚪ 计划中 · 选型未决）

> **本节不预写未核实的结论。** 桌面端目前**没有代码、没有排期**；下面给的是按平台骨架列出的**待决项**与决策前置条件，而不是已成立的技术方案。

### 9.1 待决项（骨架）

| 骨架节 | 待决内容 | 现状 |
| --- | --- | --- |
| 生态与语言 | 走 **JS 生态**（Electron / Tauri，可复用 `packages/{api,utils}`）还是**原生**（另计一类）；若走 JS 生态，`packages/ui` 的 DOM 组件可复用程度需评估 | ⚪ 未决 |
| 框架与构建 | 框架、打包（安装包格式 msi/dmg/deb）、依赖锁定 | ⚪ 未决 |
| 状态与路由 | 窗口模型（单窗口 / 多窗口）、系统快捷键、右键菜单等平台惯例 | ⚪ 未决 |
| 认证落地 | **Web 的 httpOnly Cookie 方案在桌面壳内不成立**（壳内请求可被脚本读取）→ 需改为壳内安全存储（系统钥匙串）或保留 BFF 形态 | ⚪ 未决 |
| 性能预算 | 冷启动时间、安装包体积、内存占用（指标未定义） | ⚪ 未决 |
| 平台限制 | 安装包签名（Windows Authenticode / macOS 公证与 notarization）、自动更新通道、三平台差异化 | ⚪ 未决 |
| 签名与发布 / 灰度 | **发出去的版本收不回**（与 Web 可随时回滚不同），灰度策略需单独设计 | ⚪ 未决 |

### 9.2 决策前置条件

1. 出现 Web 无法覆盖的需求：安装包分发 / 自动更新 / 离线 / 系统级集成（当前 PC 需求由 `apps/admin`、`apps/seller` 的 Web 后台覆盖）。
2. 确定是否走 JS 生态 —— 这决定它归入「JS 生态」还是独立一类（方案 §10 开放问题 3）。
3. 确定目标平台集合（仅 Windows，还是 Windows + macOS + Linux）。

---

## 十、Android 平台细则（⚪ 计划中 · 选型未决）

### 10.1 仓库现状

`apps/mobile` 是 React Native 占位项目：`package.json` 声明 `react-native ^0.76.0`（仅列在 `devDependencies`）、无 `src` 源码、`README.md` 明写「可选占位项目，PWA 先行」。它**同时服务 Android 与 iOS**，两端的选型都尚未裁决。

### 10.2 待决项（骨架）

| 骨架节 | 待决内容 | 现状 |
| --- | --- | --- |
| 生态与语言 | 候选 A：**React Native 0.76+（New Architecture）** —— 一套代码覆盖 Android + iOS，可共享 `@icedmall/api` 类型与 `@icedmall/utils`，但 **UI 组件不能复用 `apps/web`（DOM 与 Native View 不同）**；候选 B：**Kotlin + Jetpack Compose** —— 平台惯用范式优先 | ⚪ 未决 |
| 框架与构建 | Gradle / AAB 产物、依赖锁定、最低支持 API 级别 | ⚪ 未决 |
| 状态与路由 | 导航栈与返回手势（Activity/Fragment 或 Compose Navigation，或 RN 的导航库）；**与 Web 的 URL 路由不是一回事** | ⚪ 未决 |
| 认证落地 | Keystore 保护的 `EncryptedSharedPreferences`（见 §5.2） | ⚪ 未决 |
| 性能预算 | 冷启动、包体（AAB/APK）、崩溃率、页面渲染帧率 | ⚪ 未决 |
| 平台限制 | 后台执行限制、权限模型、应用市场审核；国内多渠道分发的渠道包与签名 | ⚪ 未决 |
| 签名与发布 / 灰度 | keystore 管理、Play Console 或各安卓市场；**已发布版本的灰度与回滚能力远弱于 Web** | ⚪ 未决 |

### 10.3 决策前置条件

1. PWA 是否够用：`apps/mobile/README.md` 列出的四条激活条件（深度原生能力、iOS 推送到达率、应用商店分发、性能上限）。
2. 两端是否共用一套代码 —— 选 RN 则 Android/iOS 一起决；选原生则两端各自立项。

---

## 十一、iOS 平台细则（⚪ 计划中 · 选型未决）

### 11.1 仓库现状

同 §10.1：仅有 `apps/mobile`（React Native 占位）。iOS 无任何独立代码。

### 11.2 待决项（骨架）

| 骨架节 | 待决内容 | 现状 |
| --- | --- | --- |
| 生态与语言 | 候选 A：React Native 0.76+（与 Android 共用一套代码）；候选 B：**Swift + SwiftUI** —— 平台惯用范式优先（Tab Bar / 导航栈 / 手势返回） | ⚪ 未决 |
| 框架与构建 | Xcode 工程、Swift Package / CocoaPods、最低支持 iOS 版本 | ⚪ 未决 |
| 状态与路由 | SwiftUI 的导航栈与 Tab Bar；**允许平台特有导航范式**，差异登记在 `17` §十二 | ⚪ 未决 |
| 认证落地 | Keychain（见 §5.2） | ⚪ 未决 |
| 性能预算 | 冷启动、包体（IPA）、内存峰值 | ⚪ 未决 |
| 平台限制 | 证书与描述文件、App Store 审核（含对虚拟支付/数字内容的规定，**具体以 Apple 官方政策与法务意见为准，本轮未核实**）、TestFlight 灰度 | ⚪ 未决 |
| 签名与发布 / 灰度 | 证书与描述文件管理、App Store 审核周期；**已发布版本不可回滚** | ⚪ 未决 |

### 11.3 决策前置条件

1. 同 §10.3（PWA 是否够用）。
2. iOS 相对 Android 是否**必须先行**（涉及审核周期与推送到达率），决定两端的排期先后 —— 方案 §10 开放问题 2。

---

## 十二、小程序平台细则（🟡 早期实现 · Taro 4）

### 12.1 生态与语言

小程序生态，**不引入 Web 的路由与 DOM 心智**：没有 DOM、没有 `<div>`、没有 URL 路由。

- 语言：TypeScript + React 语法（由 Taro 编译到宿主 WXML/WXSS），React 18.2。
- 宿主：**微信 `weapp` 与支付宝 `alipay` 双宿主**（`apps/miniprogram/package.json` 同时声明 `@tarojs/plugin-platform-weapp` 与 `@tarojs/plugin-platform-alipay`）。
- 组件：`@tarojs/components`（`View` / `Text` / `Image` 等），**不是** HTML 标签。

### 12.2 框架与构建

| 项 | 值 |
| --- | --- |
| 框架 | Taro 4（`@tarojs/cli` ^4.0.0、`@tarojs/react`） |
| 构建 | `@tarojs/webpack5-runner`；`pnpm --filter @icedmall/miniprogram build:weapp` / `build:alipay` |
| 包管理 | pnpm 9（workspace 成员，共享 `@icedmall/api`、`@icedmall/utils`） |
| 样式 | SCSS（`app.scss` + 页面级 `.scss`） |

### 12.3 状态与路由

- **路由是宿主的页面栈**，不是 URL：`Taro.navigateTo` / `redirectTo` / `switchTab`；页面在 `src/app.config.ts` 注册。
- 服务端状态与 Web 同源（同一份 `contract/05`），但网络层是 `Taro.request` 封装（现状见 §12.7）。

### 12.4 认证落地

- 介质：宿主 `storage`（`Taro.setStorageSync`），见 §5.2。
- 语义与 §五 一致：access + refresh、过期换新、失败清凭据。
- 后端能力：微信小程序登录与 JSAPI 支付已在后端侧就绪（依据 [21](./21-Frontend-Refactor-Readiness.md) §五 阶段 4），前端登录页骨架已建、**未接契约**。

### 12.5 性能预算

| 指标 | 目标 | 说明 |
| --- | --- | --- |
| 主包体积 | **待定** | 宿主对主包 / 分包有硬上限，**具体数值以宿主官方文档为准（本轮未核实）** |
| 首屏渲染 | **待定** | 受宿主运行环境限制，不能套用 Web 的 LCP/INP 指标 |
| 分包策略 | 待设计 | 商品详情、订单等低频页面宜进分包 |

> **不要用 Web 的预算衡量小程序**：Core Web Vitals（§8.5.2）在小程序里不适用。

### 12.6 平台限制（双宿主差异必须显式分支）

| 能力 | 微信 | 支付宝 | 处理要求 |
| --- | --- | --- | --- |
| 登录 | 宿主登录换取 code → 后端换 token | 同名但参数与返回不同 | **显式分支**，不假定通用 |
| 支付 | JSAPI / 小程序支付 | 同名 API 参数不同 | 同上 |
| 分享 / 分享码 | 分享码增量已在后端就绪 | 能力与参数不同 | 同上 |
| 订阅消息 / 模板消息 | 订阅消息 | 模板消息 | 同上 |
| 样式能力 | WXSS（CSS 子集） | ACSS（CSS 子集） | `backdrop-filter` 等能力支持不完整，**降级为半透明纯色**（具体以宿主基础库为准，待核实） |
| 包体积与本地缓存 | 有硬上限 | 有硬上限 | 见 §12.5 |

**硬规则**：**不同宿主（微信/支付宝）的 API 差异必须显式分支**，不假定通用；分支点应集中在一层适配模块，不散落在页面里。

### 12.7 现状与差距（实测）

| 项 | 实测 | 差距 |
| --- | --- | --- |
| 页面 | `src/pages/` 下 **18 个页面**（addresses / cart / checkout / coupons / favorites / flash / index / login / marketplace / notifications / order-detail / orders / pay / points / product / profile / search / sign） | 均为骨架 |
| API 层 | `src/utils/api.ts` 手写调用 | 未接生成客户端（[21](./21-Frontend-Refactor-Readiness.md) §五 阶段 4） |
| token 层 | `src/app.scss` 事实上充当 token 层 | **152 处裸 hex**（`check-design-tokens.mjs` R3 警告）；应改为与 Web 同源的样式变量层，详见 `17` §十二 |
| 认证 | 登录页骨架 | 未接契约 |

### 12.8 未决项

1. 是否只做一个宿主（当前仓库配置了双宿主，但排期未定）。
2. 最低支持的基础库版本（矩阵 §2.1 待确认项）。
3. token 层的收敛方案（`app.scss` → 与 Web 同源的变量层）。

---

## 十三、选型总结

### 13.1 Web 端选型框图

```
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│   前端技术选型：React 18.2 + Next.js 15 + TypeScript 5         │
│                                                              │
│   用户端 H5：      Next.js App Router + Tailwind + Shadcn/ui │
│   商家/平台后台：   Ant Design 5 + @ant-design/pro-components │
│   服务端状态：      TanStack Query v5                         │
│   客户端状态：      Zustand v5                                │
│   表单校验：        React Hook Form + Zod                     │
│   测试：            Vitest + Playwright                       │
│   移动端：          PWA 优先 → Taro 4 补小程序                │
│   构建：            Turbopack + pnpm + Turborepo              │
│                                                              │
│   核心理念：                                                  │
│   1. Server Components 让电商首屏零 JS                      │
│   2. ISR 让高并发商品页不触碰后端                             │
│   3. Shadcn/ui 让组件可拥有，不会被依赖抛弃                   │
│   4. Ant Design 让后台开发效率翻倍                           │
│   5. Taro 让未来小程序不是重写，而是编译                      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

> 上方框图描述的是 **Web 端（当前唯一已实现平台）** 的选型。其中的版本声明与代码实测的差异见 §8.1 漂移登记。

### 13.2 平台轴结论

| 平台 | 结论 | 细则 |
| --- | --- | --- |
| Web | **唯一已实现平台**，三端（用户端/商家/平台）同 monorepo；当前瓶颈是 API 层与契约的收敛，不是技术栈 | §八 |
| 桌面端 | **暂不立项**（PC 需求由 Web 覆盖）；立项触发条件见 §9.2 | §九 |
| Android | **未选型**：React Native（仓库占位）与 Kotlin+Compose 两条路线待裁决；PWA 先行 | §十 |
| iOS | **未选型**：React Native（仓库占位）与 Swift+SwiftUI 两条路线待裁决；PWA 先行 | §十一 |
| 小程序 | **早期实现**：Taro 4 双宿主骨架已建，未接契约；差异集中在宿主 API 与 token 层收敛 | §十二 |

**下一步**：平台轴的推进顺序（谁先开工）尚未决定 —— 见方案 §10 开放问题 2；在决定之前，非 Web 平台保持「占位骨架 + 待决项」状态，不预写实稿。
