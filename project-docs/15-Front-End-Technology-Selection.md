# 15 — 前端技术选型文档

> 选型日期：2026-06-28 | 决策者：用户确认 | 依赖：02-Architecture（后端架构）、05-API-Specification（接口契约）

---

## 一、选型原则

| 原则 | 说明 |
|------|------|
| **面向 2030** | 技术栈有 5 年以上的生命力，社区活跃，不被单一厂商绑定 |
| **SSR/SSG 优先** | 电商 SEO 是硬需求，首屏 JS 零负担（React Server Components） |
| **优雅简洁** | UI 组件可拥有（Shadcn/ui），不依赖黑盒 npm 包 |
| **高并发就绪** | ISR 静态再生成、CDN 边缘缓存、图片自动优化 |
| **渐进增强** | PWA 先行验证，小程序通过 Taro 后期补入 |
| **类型安全** | TypeScript strict 模式，前后端共享类型契约 |
| **大厂标准** | React/Next.js 全球主流，Ant Design 阿里出品，每个选择有企业验证 |

---

## 二、核心技术栈

### 2.1 总览

```
┌──────────────────────────────────────────────────────────────────┐
│                    冰美商城 前端技术栈 (2026)                       │
├──────────────────────────────────────────────────────────────────┤
│  框架      Next.js 15+ (App Router) + React 19 + TypeScript 5.x  │
│  样式      Tailwind CSS 4 + Shadcn/ui + Ant Design 5 (后台)       │
│  状态      TanStack Query v5 (服务端) + Zustand v5 (客户端)        │
│  表单      React Hook Form + Zod                                   │
│  测试      Vitest + Testing Library + Playwright (E2E)            │
│  构建      Turbopack + pnpm monorepo                               │
│  移动      PWA (Service Worker) → Taro 4 (小程序，后期)            │
│  监控      Core Web Vitals + Sentry                                │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 详细技术清单

#### 核心框架

| 技术 | 版本 | 角色 | 选型理由 |
|------|------|------|---------|
| **Next.js** | 15+ | 全栈框架 | App Router 成熟稳定，React Server Components 默认开启，ISR/SSG/SSR 三种渲染模式按需切换。Vercel 持续投入，社区第一。 |
| **React** | 19 | UI 库 | Server Components 革命性创新：商品页零客户端 JS 渲染。`use()` hook、actions、Suspense 流式渲染。全球最大生态。 |
| **TypeScript** | 5.x | 类型系统 | strict 模式。前后端共享 DTO 类型定义（从 `ia-api` 的 OpenAPI 生成）。 |

#### 样式 & 组件

| 技术 | 版本 | 角色 | 选型理由 |
|------|------|------|---------|
| **Tailwind CSS** | 4 | 原子化 CSS | 设计系统约束在 design tokens，不会产生废弃 CSS。v4 的 `@theme` + CSS-first 配置更简洁。 |
| **Shadcn/ui** | latest | 通用 UI | 组件代码直接复制到项目中（非 npm 依赖），完全可控可定制。基于 Radix 原语，无障碍访问内置。移动端适配良好。 |
| **Ant Design** | 5 | 后台 UI | 阿里出品，Table/Form/ProTable 开箱即用，企业级后台事实标准。仅用于商家后台和平台管理后台。 |
| **Lucide** | latest | 图标 | 轻量 SVG 图标，与 Shadcn/ui 配套。 |

#### 状态管理

| 技术 | 版本 | 角色 | 选型理由 |
|------|------|------|---------|
| **TanStack Query** | v5 | 服务端状态 | 请求去重、缓存失效、乐观更新、无限滚动、分页——一站式解决。比 SWR 更灵活。Next.js App Router 完美配合（Server Components 预取 + Client 水合）。 |
| **Zustand** | v5 | 客户端状态 | 购物车草稿、UI 状态、认证 Token。API 极简（无 Provider/Reducer 模板），TS 推断完美。 |

#### 表单 & 校验

| 技术 | 版本 | 角色 | 选型理由 |
|------|------|------|---------|
| **React Hook Form** | 7.x | 表单管理 | 非受控组件，性能最优（不触发全树重渲染）。与 Zod 集成无缝。 |
| **Zod** | 3.x | Schema 校验 | 类型推断 + 运行时校验合一。前后端共享校验规则（从 Zod schema 生成 TypeScript 类型）。 |

#### 测试

| 技术 | 版本 | 角色 | 选型理由 |
|------|------|------|---------|
| **Vitest** | latest | 单元/组件测试 | Vite 原生速度。与 Jest API 兼容，迁移成本低。 |
| **React Testing Library** | latest | 组件测试 | 以用户视角测试（不测实现细节）。Next.js 官方推荐。 |
| **Playwright** | latest | E2E 测试 | 多浏览器并行，trace viewer 调试，API mocking 内置。比 Cypress 更快更稳定。 |
| **MSW** | 2.x | API Mock | Service Worker 级别的请求拦截，测试和开发共用 mock handler。 |

#### 构建 & 工程化

| 技术 | 版本 | 角色 | 选型理由 |
|------|------|------|---------|
| **Turbopack** | — | 开发构建 | Next.js 内置，Rust 实现，比 Webpack 快 10x。 |
| **pnpm** | 9+ | 包管理 | 严格依赖解析、磁盘高效、monorepo workspace 原生支持。 |
| **ESLint** | 9 | 代码检查 | Flat config 模式。`eslint-config-next` + `typescript-eslint`。 |
| **Prettier** | 3 | 代码格式化 | 配合 `prettier-plugin-tailwindcss` 自动排序 class。 |
| **Husky + lint-staged** | latest | Git hooks | 提交前自动 lint + format。 |

#### 移动端 & 小程序

| 技术 | 版本 | 角色 | 选型理由 |
|------|------|------|---------|
| **PWA** | — | 渐进式 Web App | Service Worker 离线缓存、安装到桌面、推送通知。Next.js 内置 `next-pwa` 或手动配 Workbox。先以 PWA 验证业务，降低初期开发成本。 |
| **Taro** | 4.x | 跨端小程序 | 京东出品。React 语法编写，一套代码编译到微信/支付宝/抖音小程序 + H5。待 PWA 验证 PMF 后再补入。 |

#### 监控 & 分析

| 技术 | 版本 | 角色 | 选型理由 |
|------|------|------|---------|
| **Sentry** | latest | 错误追踪 | React Error Boundary 集成，源码映射，Session Replay。 |
| **Web Vitals** | — | 性能指标 | LCP/FID(INP)/CLS 上报。`@vercel/speed-insights` 开箱即用。 |
| **Google Analytics 4** | — | 用户分析 | 电商漏斗分析（浏览→加购→下单→支付转化率）。 |

---

## 三、选型对比（为何不选？）

### 3.1 核心框架对比

| 维度 | **Next.js 15 ✅** | Nuxt 4 | Remix | SvelteKit | Astro |
|------|------------------|--------|-------|-----------|-------|
| **SSR/SSG/ISR** | 🟢 全部支持 | 🟢 全部支持 | 🟢 SSR only | 🟢 全部支持 | 🟡 SSG 为主 |
| **Server Components** | 🟢 React 19 RSC | 🟡 无 RSC | 🟡 无 RSC | 🟡 无 RSC | 🟡 Islands |
| **电商 SEO** | 🟢 ISR 最佳 | 🟢 可用 | 🟢 可用 | 🟢 可用 | 🟢 最佳 |
| **交互密集(购物车/下单)** | 🟢 混合渲染 | 🟢 SPA 模式 | 🟢 迁移到 SPA | 🟢 SPA 模式 | 🔴 不适合 |
| **生态规模** | 🟢 最大 | 🟡 中等 | 🟡 增长中 | 🟡 较小 | 🟡 内容站 |
| **小程序支持** | 🟡 Taro(React) | 🟢 uni-app(Vue) | 🟡 无 | 🔴 无 | 🔴 无 |
| **中国公司使用** | 字节/TikTok/蚂蚁 | 饿了么/美团/B站 | Shopify | 少数 | 少数 |

**结论**：Next.js 在 SSR/ISR 和交互密集的平衡上最优，React Server Components 是 2026-2030 的方向。

### 3.2 UI 框架对比

| 维度 | **Shadcn/ui ✅** | Ant Design | MUI | Chakra UI | Headless UI |
|------|-----------------|------------|-----|-----------|-------------|
| **所有权** | 🟢 代码属于你 | 🟡 npm 依赖 | 🟡 npm 依赖 | 🟡 npm 依赖 | 🟢 代码属于你 |
| **定制性** | 🟢 源码直接改 | 🟡 theme token | 🟡 theme token | 🟡 theme token | 🟢 完全自由 |
| **美观度** | 🟢 现代简约 | 🟡 企业风格 | 🟡 Material | 🟢 现代简约 | 🟡 无样式 |
| **Table/Form 复杂组件** | 🟡 需自己拼 | 🟢 开箱即用 | 🟡 DataGrid 付费 | 🔴 无 | 🔴 无 |
| **移动端适配** | 🟢 可适配 | 🔴 PC only | 🔴 PC only | 🟡 可适配 | 🟢 可适配 |
| **体积** | 🟢 按需 | 🟡 较大 | 🔴 大 | 🟡 中等 | 🟢 极小 |

**结论**：两个都用——H5 用户端用 Shadcn/ui（优雅、轻量、移动适配），PC 后台用 Ant Design 5（Table/Form/ProTable 开箱即用）。

### 3.3 状态管理对比

| 维度 | **TanStack Query ✅** | SWR | Redux Toolkit | Apollo Client |
|------|----------------------|-----|---------------|---------------|
| **缓存策略** | 🟢 最丰富 | 🟡 够用 | 🔴 需手写 | 🟡 GraphQL only |
| **乐观更新** | 🟢 内置 | 🟡 需配置 | 🟡 需手写 | 🟢 内置 |
| **无限滚动** | 🟢 `useInfiniteQuery` | 🟢 `useSWRInfinite` | 🔴 需手写 | 🟡 需手写 |
| **DevTools** | 🟢 专用工具 | 🟡 无 | 🟢 Redux DevTools | 🟡 Apollo DevTools |
| **Next.js 兼容** | 🟢 完美 | 🟢 完美 | 🟡 需配置 | 🟡 GraphQL 限定 |

### 3.4 对比：为什么不用 Vue？

Vue 生态在中国确实更流行（饿了么/美团/有赞），但：

| 维度 | React/Next.js | Vue/Nuxt |
|------|---------------|----------|
| **Server Components** | ✅ React 19 RSC | ❌ 无等价物 |
| **ISR 粒度** | ✅ `revalidate` 60s/page | ⚠️ 有但生态小 |
| **TypeScript 体验** | ✅ JSX 天然类型推断 | ⚠️ 模板 TS 推断有限 |
| **小程序** | ⚠️ Taro（React 版） | ✅ uni-app 更成熟 |
| **全球人才池** | ✅ 最大 | ⚠️ 中国为主 |
| **AI 编码辅助** | ✅ 训练数据最多 | ⚠️ 较少 |

小程序方面的劣势由 Taro 4 弥补——京东的 Taro 在 React 生态小程序编译方面已经足够成熟。

---

## 四、组件架构

### 4.1 项目结构（Monorepo）

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

### 4.2 渲染策略决策树

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

### 4.3 页面 → 渲染模式映射

| 页面 | 渲染模式 | 理由 |
|------|---------|------|
| 首页 | SSG + ISR 60s | 内容相对稳定，需要秒开 + SEO |
| 商品列表 | ISR 60s | 商品变化中等，需要 SEO + 搜索友好 |
| 商品详情 | ISR 300s + Client islands | 基本信息稳定，库存/价格 client fetch |
| 分类页 | SSG | 类目结构变化极少 |
| 购物车 | Client Component | 纯交互，无需 SEO |
| 下单/支付 | Client Component | 表单密集，需要实时校验 |
| 订单列表/详情 | SSR (auth required) | 需要登录，但不能缓存 |
| 登录/注册 | Client Component | 表单交互 |
| 商家后台 | SPA (Client only) | 纯管理功能，无需 SEO |
| 平台管理后台 | SPA (Client only) | 纯管理功能，无需 SEO |

---

## 五、认证方案

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

### 5.2 中间件路由守卫（Next.js Middleware）

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

---

## 六、性能策略

### 6.1 高并发场景设计

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

| 层级 | 优化手段 | 目标 |
|------|---------|------|
| **静态资源** | CDN + immutable cache + 图片 WebP/AVIF 自动转换 | 图片 LCP < 2.5s |
| **页面渲染** | ISR（商品列表/详情）+ SSG（首页/分类） | TTFB < 200ms |
| **API 缓存** | TanStack Query 客户端缓存 + `staleTime` 配置 | 减少 70% 重复请求 |
| **代码分割** | Next.js Route-based splitting + `dynamic(() => import())` | 首屏 JS < 100KB |
| **图片优化** | `next/image` (自动 WebP/AVIF, lazy loading, blur placeholder) | 图片体积减少 60% |
| **字体** | `next/font` (自托管 Google Fonts, 无外部请求) | FOIT 0ms |
| **预取** | `<Link prefetch>` 视口内链接自动预取 | 页面切换 < 100ms |
| **PWA** | Service Worker 离线缓存 + Cache-Control: stale-while-revalidate | 二次访问秒开 |

### 6.2 Core Web Vitals 目标

| 指标 | 目标 | 说明 |
|------|------|------|
| **LCP** (最大内容绘制) | < 2.5s | 商品首图、轮播 Banner |
| **INP** (交互到下次绘制) | < 200ms | 加购按钮、结算流程 |
| **CLS** (累计布局偏移) | < 0.1 | 防止图片加载导致的布局跳动 |

---

## 七、测试策略

### 7.1 测试分布（参考 11-Test-Strategy）

| 层级 | 工具 | 覆盖目标 | 示例 |
|------|------|---------|------|
| **单元测试** | Vitest | 工具函数、Zod schema、状态管理 | `formatPrice(1250) → "12.50"` |
| **组件测试** | Vitest + RTL | UI 交互、表单校验、边界状态 | 购物车数量加减、表单错误提示 |
| **集成测试** | Playwright + MSW | 关键业务流程的端到端 | 浏览商品→加购→下单→支付成功 |
| **视觉回归** | Percy / Chromatic | 组件视觉一致性 | 按钮/卡片/表单样式变更检测 |
| **性能测试** | Lighthouse CI | PR 级别性能门禁 | LCP > 3s 阻止合并 |

### 7.2 关键 E2E 场景

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

---

## 八、部署架构

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

| 组件 | 部署方式 | 说明 |
|------|---------|------|
| **静态资源** | CDN (阿里云 CDN/Cloudflare) | `_next/static/` + `public/` 目录 |
| **Next.js App** | Docker 容器 (K8s/Compose) | `next start` 生产模式，多实例 + 负载均衡 |
| **ISR 缓存** | 共享 Redis / 文件系统 | 多实例间 ISR 缓存一致性 |
| **图片优化** | `next/image` + 独立图片服务 | 生产环境可替换为阿里云图片处理 |

---

## 九、依赖版本锁定

```json
{
  "dependencies": {
    "next": "^15.0.0",
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
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

---

## 十、与后端契约对接

### 10.1 API 协议

| 维度 | 约定 |
|------|------|
| **Base URL** | `https://api.icedmall.com` (外部) / `http://gate-service:8080` (内部) |
| **Content-Type** | `application/json; charset=utf-8` |
| **Auth** | Cookie: `access_token` (httpOnly, Secure, SameSite=Lax, max-age=1800) |
| **响应格式** | `{ "code": 200, "msg": "success", "data": T }` (参考 `ia-common Result<T>`) |
| **错误格式** | `{ "code": 40001, "msg": "手机号已注册", "data": null }` |
| **分页格式** | `{ "code": 200, "data": { "records": T[], "total": 100, "size": 20, "current": 1 } }` |

### 10.2 类型生成

从 `ia-api` 模块的 Knife4j OpenAPI 规范生成 TypeScript 类型：

```bash
# 从后端 OpenAPI JSON 生成 TypeScript 类型
npx openapi-typescript http://localhost:8080/v3/api-docs -o packages/api/types/schema.ts
```

这确保前后端类型契约永不漂移。

---

## 十一、禁止事项

| ❌ 禁止 | ✅ 替代 |
|--------|--------|
| Redux (模板太多) | Zustand + TanStack Query |
| CSS-in-JS 运行时 (RSC 不兼容) | Tailwind CSS + CSS Modules |
| class 组件 | React 函数组件 + Hooks |
| `any` 类型 | 严格 TypeScript + Zod 推断 |
| 直接操作 localStorage Token | httpOnly Cookie |
| Webpack 自定义配置 | Turbopack / 零配置 |
| npm/yarn | pnpm (Monorepo 最佳) |
| Axios (非必需) | 原生 `fetch` + TanStack Query (RSC 兼容) |
| 在 Client Component 中 `fetch` 数据库 | Server Actions / API Routes |
| 图片用 `<img>` | `next/image` (自动优化) |

---

## 十二、选型总结

```
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│   前端技术选型：React 19 + Next.js 15 + TypeScript 5         │
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
