# 18 — 前端实施方案文档

> 最后更新: 2026-08-02 | 版本: V1.0
>
> 依赖: [16-Frontend-Product-Design](./16-Frontend-Product-Design.md) · [17-Frontend-Design-System](./17-Frontend-Design-System.md) · [05-API-Specification](./05-API-Specification.md) · [15-Front-End-Technology-Selection](./15-Front-End-Technology-Selection.md)
>
> 前置条件: 设计系统 (§17) 和产品设计 (§16) 已定稿，本文档进入工程实施规划。

---

## 一、实施总览

### 1.1 核心理念

```
不让 AI 一次生成全部。
分阶段、分层次、步步验证。

Phase 1 → 地基 (设计系统落地)
Phase 2 → 品牌 (品牌主页)
Phase 3 → 交易 (商城核心链路)
Phase 4 → 数据 (接入真实 API)
Phase 5 → 优化 (性能 + 测试)
Phase 6 → 扩展 (个人中心 + 后台)
```

### 1.2 开发环境

```bash
# 前置条件
Node.js >= 20
pnpm >= 9

# 创建 Monorepo
mkdir front-end && cd front-end
pnpm init
# 配置 pnpm-workspace.yaml + turbo.json

# 创建 apps
mkdir -p apps/storefront apps/admin apps/seller
mkdir -p packages/ui packages/api packages/auth packages/config packages/utils

# 安装核心依赖
pnpm add -w next@latest react@latest react-dom@latest typescript@latest
pnpm add -w tailwindcss@latest @tailwindcss/postcss
pnpm add -w @tanstack/react-query zustand react-hook-form zod
pnpm add -w lucide-react next-themes
pnpm add -w -D vitest @testing-library/react @playwright/test msw
```

### 1.3 工程约束

| 约束 | 标准 | 检查工具 |
|------|------|---------|
| TypeScript | strict 模式，禁止 `any` | `tsc --noEmit` |
| ESLint | Flat config, `eslint-config-next` | `eslint .` |
| Prettier | 3.x + `prettier-plugin-tailwindcss` | `prettier --check` |
| 测试 | Vitest ≥ 80% 工具函数, Playwright ≥ 5 核心流程 | CI gate |
| 性能 | Lighthouse ≥ 90, LCP < 2.5s | Lighthouse CI |
| 包管理 | pnpm only | `engine-strict=true` |

---

## 二、Phase 1 — 设计系统落地 (Day 1-3)

> **目标**: 将 §17 的 Design Tokens 落地为可运行的 Tailwind 配置 + 基础组件。

### 2.1 任务清单

| # | 任务 | 产出 | 优先级 |
|---|------|------|--------|
| P1.1 | Monorepo 骨架搭建 | `pnpm-workspace.yaml`, `turbo.json`, 各 package 的 `package.json` | P0 |
| P1.2 | Tailwind CSS v4 `@theme` 配置 | `packages/config/tailwind.config.ts` — 所有 Design Tokens | P0 |
| P1.3 | 全局 CSS 变量 + 字体加载 | `apps/storefront/app/globals.css` — `next/font` 加载 Inter + Noto Sans SC + Playfair Display | P0 |
| P1.4 | GlassCard 组件 | `packages/ui/components/shared/GlassCard.tsx` | P0 |
| P1.5 | GlassButton 组件 (4 variants) | `packages/ui/components/shared/GlassButton.tsx` | P0 |
| P1.6 | GlassInput 组件 | `packages/ui/components/shared/GlassInput.tsx` | P1 |
| P1.7 | GlassModal 组件 | `packages/ui/components/shared/GlassModal.tsx` | P1 |
| P1.8 | Skeleton 组件 | `packages/ui/components/shared/Skeleton.tsx` — shimmer 动画 | P0 |
| P1.9 | EmptyState + ErrorState 组件 | `packages/ui/components/shared/EmptyState.tsx`, `ErrorState.tsx` | P1 |
| P1.10 | ScrollReveal 组件 | `packages/ui/components/shared/ScrollReveal.tsx` — IntersectionObserver | P1 |
| P1.11 | ImageWithBlur 组件 | `packages/ui/components/shared/ImageWithBlur.tsx` — 渐进式图片 | P1 |
| P1.12 | 组件 Storybook (可选) | 基础组件可视化验收 | P2 |

### 2.2 验收标准

- [ ] `pnpm dev` 启动 storefront，看到 Tailwind 样式生效
- [ ] GlassCard 在所有视口正确渲染，hover/active 效果正常
- [ ] GlassButton 4 种 variant 均正确
- [ ] 暗色模式 / 亮色模式 CSS 变量正确切换
- [ ] 字体加载无 FOIT (Flash of Invisible Text)
- [ ] Skeleton shimmer 动画 60fps

### 2.3 不在此阶段

- ❌ 任何业务逻辑
- ❌ API 调用
- ❌ 路由
- ❌ 状态管理

---

## 三、Phase 2 — 品牌主页 (Day 4-8)

> **目标**: 实现品牌主页 `/` — 沉浸式品牌体验，100% 视觉还原设计稿。

### 3.1 任务清单

| # | 任务 | 产出 | 优先级 |
|---|------|------|--------|
| P2.1 | `(brand)/layout.tsx` | 品牌 Layout: 透明毛玻璃导航 + 背景 | P0 |
| P2.2 | HeroSection — Three.js 场景 | Canvas 东方庭院场景 + 昼夜循环 Shader | P0 |
| P2.3 | HeroSection — 视差光照 | 鼠标移动光影跟随 (requestAnimationFrame + CSS variables) | P1 |
| P2.4 | HeroSection — 中央内容 | 品牌名称 + 理念文字 + 探索商城按钮 | P0 |
| P2.5 | BrandPhilosophy Section | 品牌理念区域: 文字 + 留白 + 植物装饰 | P0 |
| P2.6 | FeaturedWorks Section | 精选作品横向滚动 (原生 CSS scroll-snap) | P0 |
| P2.7 | CreatorSpace Section | 创作者空间: 毛玻璃卡片 + 理念文字 | P1 |
| P2.8 | MarketplaceEntry Section | 进入商城入口: 巨型玻璃按钮 + 过渡动画 | P0 |
| P2.9 | ScrollReveal 集成 | 各 Section 滚动渐显 + stagger 延迟 | P1 |
| P2.10 | 响应式适配 | 移动端品牌主页布局 | P1 |

### 3.2 技术决策

**Three.js 场景**:
```tsx
// 使用 @react-three/fiber + @react-three/drei
// 品牌主页 Hero 背景

import { Canvas } from '@react-three/fiber';
import { Environment, OrbitControls } from '@react-three/drei';

// 场景组件 — 仅在客户端加载
const GardenScene = dynamic(() => import('./_scenes/GardenScene'), {
  ssr: false,
  loading: () => <div className="absolute inset-0 bg-ink" />
});
```

**性能要求**:
- Canvas 仅渲染背景 (不参与页面交互)
- 使用 `frameloop="demand"` 仅在滚动/鼠标移动时渲染
- 几何体面数 < 10k
- Shader 复杂度: 仅色温 + 光照方向变化，无后处理
- 移动端降级: 静态渐变背景

### 3.3 验收标准

- [ ] 品牌主页全屏展示，无滚动条异常
- [ ] 滚动白天→黄昏→夜晚过渡平滑 (30fps+)
- [ ] 鼠标视差光照跟随在 60fps
- [ ] "探索商城"按钮跳转到 `/marketplace` (目前可 404)
- [ ] 移动端降级为静态背景
- [ ] Lighthouse 评分 ≥ 85 (品牌主页允许略低于商城)
- [ ] 页面加载时间 < 3s (含 Three.js)

---

## 四、Phase 3 — 商城核心链路 (Day 9-18)

> **目标**: 实现商城首页 + 商品详情 + 购物车 + 结算 — 核心交易闭环。

### 4.1 Phase 3.1 — 商城首页 (Day 9-11)

| # | 任务 | 产出 | API |
|---|------|------|-----|
| P3.1 | `(marketplace)/layout.tsx` | 商城 Layout: Header + Main + Footer | — |
| P3.2 | Header 导航栏 | Logo + SearchBar + 分类下拉 + 购物车图标 + 用户菜单 | — |
| P3.3 | Footer | 品牌信息 + 链接 + 社交媒体 + 版权 | — |
| P3.4 | Hero Banner | 轮播 Banner (Swiper/Embla) | `GET /api/home/config` |
| P3.5 | 分类入口 (CategoryEntry) | 水平滚动分类图标 | `GET /api/item/category/tree` |
| P3.6 | 精选推荐 (FeaturedProducts) | 横向滚动 ProductCard 列表 | `GET /api/item/product/page?sort=sales` |
| P3.7 | 新品上市 (NewArrivals) | 2×4 商品网格 | `GET /api/item/product/page?sort=newest` |
| P3.8 | 活动专区 (ActivityZone) | 秒杀 + 领券入口卡片 | `GET /api/flash`, `GET /api/coupon/template` |
| P3.9 | ProductCard 组件 | 带 hover 效果的商品卡片 | — |
| P3.10 | ProductGrid 组件 | 响应式商品网格 | — |
| P3.11 | ProductHorizontalScroll 组件 | 横向滚动容器 + 箭头导航 | — |

### 4.2 Phase 3.2 — 搜索 (Day 12-13)

| # | 任务 | 产出 | API |
|---|------|------|-----|
| P3.12 | SearchBar 组件 | 搜索框 + 焦点展开 + 建议下拉 | `GET /api/search/suggest` |
| P3.13 | 搜索页 | 搜索结果列表 + 筛选面板 | `GET /api/search/product` |
| P3.14 | SearchFilter 组件 | 分类/价格/品牌/排序筛选 | — |
| P3.15 | HotKeywords 组件 | 热门搜索标签云 | `GET /api/search/hot` |
| P3.16 | SearchHistory 组件 | 搜索历史 (localStorage + API) | `GET /api/search/history` |

### 4.3 Phase 3.3 — 商品详情 (Day 14-15)

| # | 任务 | 产出 | API |
|---|------|------|-----|
| P3.17 | 商品图片轮播 | 多图滑动 + 缩略图导航 + 点击放大 (Lightbox) | — |
| P3.18 | 商品信息区 | 名称/价格/标签/优惠券提示 | `GET /api/item/product/{id}/detail` |
| P3.19 | SkuSelector 组件 | 多维度规格选择 (颜色/尺寸) | `GET /api/item/product/{id}/skus` |
| P3.20 | QuantityStepper 组件 | 数量加减 (min 1, max stock) | — |
| P3.21 | 店铺卡片 | 店铺信息 + 关注 + 进店 | `GET /api/shop/{sellerId}` |
| P3.22 | 商品详情 Tab | 图文详情 / 规格参数 | — |
| P3.23 | 评价摘要 | 评分分布 + 标签 | `GET /api/item/review/product/{id}/summary` |
| P3.24 | 评价列表 | 筛选 + 分页 | `GET /api/item/review/product/{id}/filter` |
| P3.25 | 推荐商品 | "看了又看"横向滚动 | `GET /api/item/product/page` |
| P3.26 | 加购 / 立即购买 | 按钮交互 + Toast 反馈 | `POST /api/cart/item`, 跳转结算 |

### 4.4 Phase 3.4 — 购物车 (Day 16-17)

| # | 任务 | 产出 | API |
|---|------|------|-----|
| P3.27 | 购物车页 | 按店铺分组展示 | `GET /api/cart/grouped` |
| P3.28 | CartItem 组件 | 商品行: 图片/名称/规格/价格/数量/小计 | — |
| P3.29 | 数量修改 | + / - 按钮 + 输入框 (debounced API call) | `PUT /api/cart/item` |
| P3.30 | 选中/取消 + 全选 | Checkbox + 汇总栏联动 | `PATCH /api/cart/item/{skuId}/selected` |
| P3.31 | 删除商品 | 单删 + 批量删 | `DELETE /api/cart/item/{skuId}` |
| P3.32 | 空购物车 | 空状态 + "去逛逛" CTA | — |
| P3.33 | 底部汇总栏 | 合计金额 + 已选数量 + 结算按钮 | — |
| P3.34 | CartDrawer 组件 | 侧边购物车抽屉 (悬浮快捷入口) | — |

### 4.5 Phase 3.5 — 结算 & 支付 (Day 18)

| # | 任务 | 产出 | API |
|---|------|------|-----|
| P3.35 | 结算页 | 地址 + 商品清单 + 优惠券 + 备注 + 金额汇总 | — |
| P3.36 | AddressSelector 组件 | 地址单选列表 + 新增/编辑 inline | `GET /api/user/address/list`, `POST /api/user/address/add` |
| P3.37 | CouponSelector 组件 | 可用优惠券列表 + 一键选用 | `POST /api/coupon/available/filter` |
| P3.38 | 订单金额汇总 | 商品总额 - 优惠券 = 实付金额 | — |
| P3.39 | 提交订单 | 调用 API + loading + 成功跳转支付 | `POST /api/trade/order` |
| P3.40 | 支付页 | 微信二维码 + 支付宝 + 余额支付选择 | `POST /api/pay/order/{orderNo}` |
| P3.41 | 支付状态轮询 | 3s 轮询 + 超时提示 + 成功跳转 | `GET /api/pay/order/{orderNo}/status` |
| P3.42 | 支付结果页 | 成功/失败状态 + 查看订单 + 继续购物 | — |

### 4.6 验收标准 (Phase 3 完成)

- [ ] 完整交易闭环可走通: 浏览 → 搜索 → 详情 → 加购 → 结算 → 支付
- [ ] 所有商品卡片 hover 效果 60fps
- [ ] 搜索响应 < 300ms (含 API)
- [ ] 购物车数量修改 debounce 防抖
- [ ] 结算页地址/优惠券 inline 选择，无页面跳转
- [ ] 移动端适配: 所有页面在 375px 宽度正常显示
- [ ] 骨架屏在所有列表页生效

---

## 五、Phase 4 — 接入真实 API (Day 19-22)

> **目标**: 用 MSW 建立 API Mock → 前端开发完成 → 切换到真实后端。

### 5.1 任务清单

| # | 任务 | 产出 | 优先级 |
|---|------|------|--------|
| P4.1 | API 类型生成 | 从后端 OpenAPI spec 生成 TypeScript 类型到 `packages/api/types/` | P0 |
| P4.2 | Zod Schema 库 | `packages/utils/validate.ts` — 前端校验规则 | P0 |
| P4.3 | Fetch Client | `packages/api/client.ts` — Base URL + JWT interceptor + 错误处理 | P0 |
| P4.4 | TanStack Query hooks — 商品 | `useProducts`, `useProduct`, `useProductDetail` | P0 |
| P4.5 | TanStack Query hooks — 购物车 | `useCart`, `useAddToCart`, `useUpdateCartItem`, `useRemoveCartItem` | P0 |
| P4.6 | TanStack Query hooks — 订单 | `useOrders`, `useOrder`, `useCreateOrder` | P0 |
| P4.7 | TanStack Query hooks — 支付 | `usePayOrder`, `usePayStatus` | P0 |
| P4.8 | TanStack Query hooks — 搜索 | `useSearch`, `useHotKeywords`, `useSuggestions` | P0 |
| P4.9 | TanStack Query hooks — 用户 | `useUserInfo`, `useAddresses`, `useFavorites`, `useNotifications` | P1 |
| P4.10 | TanStack Query hooks — 营销 | `useCoupons`, `useFlashSales`, `useClaimCoupon`, `useFlashBuy` | P1 |
| P4.11 | TanStack Query hooks — AI | `useAiChat` (Vercel AI SDK `useChat`) | P1 |
| P4.12 | MSW Mock handlers | 为所有 API 提供 mock 数据, 前端可脱离后端开发 | P0 |
| P4.13 | Query Key 工厂 | `packages/api/queries/` — 统一 query key 管理 | P0 |
| P4.14 | 乐观更新 | 购物车加减删使用 `onMutate` + `onError` rollback | P1 |

### 5.2 Fetch Client 实现

```typescript
// packages/api/client.ts
const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080';

class ApiClient {
  private async request<T>(path: string, options?: RequestInit): Promise<T> {
    const res = await fetch(`${API_BASE}${path}`, {
      ...options,
      credentials: 'include',  // Cookie 携带 JWT
      headers: {
        'Content-Type': 'application/json',
        ...options?.headers,
      },
    });

    // 401 → 触发 token 刷新
    if (res.status === 401) {
      const refreshed = await this.refreshToken();
      if (refreshed) return this.request(path, options);
      // 刷新失败 → 跳转登录
      window.location.href = '/auth/login';
      throw new Error('Unauthorized');
    }

    const json = await res.json();
    if (json.code !== 200) {
      throw new ApiError(json.code, json.msg);
    }
    return json.data as T;
  }

  async get<T>(path: string, params?: Record<string, any>): Promise<T> {
    const query = params ? `?${new URLSearchParams(params)}` : '';
    return this.request<T>(`${path}${query}`);
  }

  async post<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>(path, { method: 'POST', body: JSON.stringify(body) });
  }

  async put<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>(path, { method: 'PUT', body: JSON.stringify(body) });
  }

  async patch<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>(path, { method: 'PATCH', body: JSON.stringify(body) });
  }

  async delete<T>(path: string): Promise<T> {
    return this.request<T>(path, { method: 'DELETE' });
  }

  private async refreshToken(): Promise<boolean> {
    // 读取 refresh_token cookie, 调 /api/auth/refresh
    // 成功后新的 access_token 由后端 Set-Cookie
    try {
      await fetch(`${API_BASE}/api/auth/refresh`, { method: 'POST', credentials: 'include' });
      return true;
    } catch {
      return false;
    }
  }
}

export const api = new ApiClient();
```

### 5.3 Query Key 工厂示例

```typescript
// packages/api/queries/product.ts
export const productKeys = {
  all: ['products'] as const,
  lists: () => [...productKeys.all, 'list'] as const,
  list: (filters: ProductPageParams) => [...productKeys.lists(), filters] as const,
  details: () => [...productKeys.all, 'detail'] as const,
  detail: (id: number) => [...productKeys.details(), id] as const,
  skus: (id: number) => [...productKeys.all, 'skus', id] as const,
};
```

### 5.4 验收标准

- [ ] `pnpm dev` 使用 MSW mock，所有页面正常渲染 mock 数据
- [ ] 切换到真实后端 (改 env var)，核心交易链路正常
- [ ] TanStack Query Devtools 显示请求去重生效
- [ ] 购物车乐观更新: 网络慢时 UI 即时响应，失败时 rollback
- [ ] JWT 过期自动刷新，用户无感知

---

## 六、Phase 5 — 性能优化 & 测试 (Day 23-26)

> **目标**: Lighthouse ≥ 90, 核心 E2E 通过, PWA 就绪。

### 6.1 性能优化

| # | 任务 | 产出 | 目标 |
|---|------|------|------|
| P5.1 | ISR 配置 | 商品列表 `revalidate=60`, 商品详情 `revalidate=300`, 秒杀 `revalidate=10` | TTFB < 200ms |
| P5.2 | 图片优化 | `next/image` + WebP/AVIF + blurDataURL + 懒加载 | 图片体积 -60% |
| P5.3 | 字体优化 | `next/font` + `subset` (仅中文字符集按需) | FOIT 0ms |
| P5.4 | Bundle 分析 | `@next/bundle-analyzer` — 识别大 chunk | 首屏 JS < 100KB |
| P5.5 | 代码分割 | `dynamic(() => import(...))` + `suspense` | 非首屏组件按需加载 |
| P5.6 | Link prefetch | 视口内 `<Link>` 自动 prefetch | 页面切换 < 100ms |
| P5.7 | 动画审查 | 检查所有动画是否仅用 transform/opacity | 60fps |
| P5.8 | Lighthouse CI | 配置 GitHub Actions 门禁 | 评分 ≥ 90 |

### 6.2 测试

| # | 任务 | 工具 | 覆盖 |
|---|------|------|------|
| P5.9 | 工具函数单测 | Vitest | `formatPrice`, Zod schemas, query keys |
| P5.10 | 组件测试 | Vitest + RTL | GlassCard, GlassButton, ProductCard, SkuSelector, QuantityStepper |
| P5.11 | 购物车逻辑测试 | Vitest | 加购/去重/选中/全选/计算总价 |
| P5.12 | E2E — 浏览商品 | Playwright | 首页→分类→详情, 验证 LCP < 2.5s |
| P5.13 | E2E — 下单支付 | Playwright + MSW | 登录→加购→结算→提交→支付成功 |
| P5.14 | E2E — 秒杀 | Playwright + MSW | 秒杀页→抢购→售罄/成功 |
| P5.15 | E2E — 移动端 | Playwright (iPhone 14 viewport) | 移动端适配 + Touch 交互 |
| P5.16 | 视觉回归 (可选) | Percy / Chromatic | 卡片/按钮组件视觉一致性 |

### 6.3 PWA 配置

```typescript
// next.config.ts — PWA
import withPWA from 'next-pwa';

const nextConfig = withPWA({
  dest: 'public',
  register: true,
  skipWaiting: true,
  disable: process.env.NODE_ENV === 'development',
  runtimeCaching: [
    {
      urlPattern: /^https?:\/\/cdn\.icedmall\.com\/.*/i,
      handler: 'CacheFirst',
      options: {
        cacheName: 'images',
        expiration: { maxEntries: 200, maxAgeSeconds: 60 * 60 * 24 * 30 },
      },
    },
    {
      urlPattern: /\/api\//i,
      handler: 'NetworkFirst',
      options: {
        cacheName: 'api-cache',
        expiration: { maxEntries: 50, maxAgeSeconds: 60 * 5 },
      },
    },
  ],
})(nextConfig);
```

### 6.4 验收标准

- [ ] Lighthouse Desktop ≥ 95, Mobile ≥ 90
- [ ] LCP < 2.5s, INP < 200ms, CLS < 0.1
- [ ] 所有 E2E 测试通过
- [ ] PWA 可安装, 离线可查看缓存的商品页
- [ ] CI 门禁: lint + typecheck + test + Lighthouse

---

## 七、Phase 6 — 扩展功能 (Day 27-35+)

> **目标**: 个人中心 + 秒杀 + AI + 消息 + 商家/管理后台。

### 7.1 Phase 6.1 — 个人中心 (Day 27-29)

| 模块 | 页面 | API 依赖 |
|------|------|---------|
| 认证 | 登录/注册/找回密码 | `POST /api/auth/login`, `/register`, `/reset-password` |
| 资料 | 个人中心 + 编辑 + 头像上传 | `GET /api/user/info`, `PATCH /api/user/profile`, `POST /api/user/profile/avatar` |
| 地址 | 地址列表 + 新增/编辑/删除/设默认 | `GET/POST/PUT/DELETE /api/user/address/*` |
| 订单 | 订单列表 + 详情 + 取消 + 确认收货 | `GET /api/trade/order/page`, `/{orderNo}`, `/cancel`, `/confirm` |
| 收藏 | 收藏列表 + 取消收藏 | `GET/DELETE /api/user/favorite` |
| 优惠券 | 可用/已用/已过期 Tab | `GET /api/coupon/available`, `/used` |
| 积分 | 积分余额 + 明细 | `GET /api/user/points/balance`, `/history` |
| 签到 | 签到日历 + 连续签到 | `POST /api/user/sign`, `GET /api/user/sign/status` |
| 售后 | 申请 + 列表 | `POST /api/after-sale`, `GET /api/after-sale` |

### 7.2 Phase 6.2 — 秒杀频道 (Day 30)

| # | 任务 | API |
|---|------|-----|
| P6.1 | 秒杀频道页 | `GET /api/flash`, `GET /api/flash/timeline` |
| P6.2 | FlashSaleCard 组件 | 倒计时 + 进度条 + 抢购按钮 |
| P6.3 | FlashCountdown 组件 | 服务端时间校准 + 毫秒级倒计时 |
| P6.4 | FlashProgress 组件 | 库存进度条 |
| P6.5 | 抢购交互 | `POST /api/flash/buy` → loading → 成功/失败/售罄 |

### 7.3 Phase 6.3 — AI 助手 & 消息 (Day 31-32)

| # | 任务 | API |
|---|------|-----|
| P6.4 | AiChatPanel 组件 | `POST /api/ai/chat/stream` (SSE + Vercel AI SDK `useChat`) |
| P6.5 | AiProductRecommend 组件 | AI 推荐商品卡片渲染 |
| P6.6 | AiConversationHistory 组件 | `GET /api/ai/conversations` 会话列表 |
| P6.7 | ChatWindow 组件 | WebSocket STOMP `/ws/chat` |
| P6.8 | ConversationList 组件 | `GET /api/chat/conversations` |

### 7.4 Phase 6.4 — 商家后台 + 管理后台 (Day 33-35+)

| 模块 | 关键页面 | 技术 |
|------|---------|------|
| 商家后台 | 仪表盘、商品管理(ProTable)、订单管理、发货、优惠券、结算、店铺设置 | Ant Design 5 + ProComponents |
| 管理后台 | 数据仪表盘、用户管理、商家审核、商品管理、订单管理、分类管理、首页装修、优惠券管理、秒杀管理、财务管理、角色权限、操作日志 | Ant Design 5 + ProComponents |

**Ant Design 集成**:
```bash
pnpm add antd @ant-design/pro-components -F apps/admin
pnpm add antd @ant-design/pro-components -F apps/seller
```

后台页面是**纯客户端渲染 (SPA 模式)**，不需要 SEO。使用 Ant Design ProLayout 开箱即用的侧边栏 + 面包屑 + 权限路由。

---

## 八、认证方案实施

### 8.1 认证流程

```
1. 用户提交登录表单
2. POST /api/auth/login → 后端 Set-Cookie: access_token (httpOnly)
3. Next.js Middleware 拦截需要认证的路由
4. Middleware 验证 access_token (RS256 公钥)
   ├── 有效 → 放行, headers 附加 userId/username
   ├── 过期 → 尝试 refresh_token → 新 access_token
   └── 失败 → 302 redirect /auth/login
5. 前端不存储 Token, 不操作 Cookie, 全由 httpOnly Cookie 管理
```

### 8.2 Middleware 实现

```typescript
// packages/auth/middleware.ts
import { NextRequest, NextResponse } from 'next/server';
import { jwtVerify, importSPKI } from 'jose';

const PUBLIC_KEY = process.env.JWT_PUBLIC_KEY!;  // RS256 公钥

const PROTECTED_PATHS = [
  '/cart', '/checkout', '/orders', '/pay',
  '/user', '/sign', '/ai', '/chat',
  '/seller', '/admin',
];

const PUBLIC_PATHS = [
  '/auth/login', '/auth/register', '/auth/reset-password',
  '/api/auth', '/oauth2',
];

export async function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // 公开路径直接放行
  if (PUBLIC_PATHS.some(p => pathname.startsWith(p))) {
    return NextResponse.next();
  }

  // 需要认证的路径
  if (PROTECTED_PATHS.some(p => pathname.startsWith(p))) {
    const accessToken = request.cookies.get('access_token')?.value;

    if (!accessToken) {
      return NextResponse.redirect(new URL('/auth/login', request.url));
    }

    try {
      const publicKey = await importSPKI(PUBLIC_KEY, 'RS256');
      const { payload } = await jwtVerify(accessToken, publicKey);

      // 附加用户信息到 headers (后端 Gateway 也需要)
      const response = NextResponse.next();
      response.headers.set('X-User-Id', String(payload.user_id));
      response.headers.set('X-Username', String(payload.username));
      return response;
    } catch {
      // Token 过期或无效
      const refreshToken = request.cookies.get('refresh_token')?.value;
      if (refreshToken) {
        // 尝试刷新 (简化: 重定向到 refresh API)
        // 生产环境应在此直接调用后端 refresh
        const refreshRes = await fetch(`${request.nextUrl.origin}/api/auth/refresh?refresh_token=${refreshToken}`, {
          method: 'POST',
        });
        if (refreshRes.ok) {
          // 刷新成功, 带有新的 Set-Cookie, 继续请求
          return NextResponse.next();
        }
      }

      return NextResponse.redirect(new URL('/auth/login', request.url));
    }
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    '/((?!_next/static|_next/image|favicon.ico|public).*)',
  ],
};
```

### 8.3 Zustand Auth Store

```typescript
// packages/auth/store.ts
import { create } from 'zustand';

interface AuthState {
  user: UserInfoResp | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  setUser: (user: UserInfoResp | null) => void;
  fetchUser: () => Promise<void>;
  logout: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: false,
  isLoading: true,

  setUser: (user) => set({ user, isAuthenticated: !!user }),

  fetchUser: async () => {
    try {
      const user = await api.get<UserInfoResp>('/api/user/info');
      set({ user, isAuthenticated: true, isLoading: false });
    } catch {
      set({ user: null, isAuthenticated: false, isLoading: false });
    }
  },

  logout: async () => {
    await api.post('/api/auth/logout');
    set({ user: null, isAuthenticated: false });
  },
}));
```

---

## 九、目录结构 (最终)

```
front-end/
├── apps/
│   ├── storefront/                  # 用户端 H5 + PWA
│   │   ├── app/
│   │   │   ├── (brand)/             # 品牌主页
│   │   │   │   ├── layout.tsx
│   │   │   │   ├── page.tsx
│   │   │   │   └── _scenes/
│   │   │   │       └── GardenScene.tsx   # Three.js 场景
│   │   │   ├── (marketplace)/       # 商城
│   │   │   │   ├── layout.tsx
│   │   │   │   ├── page.tsx
│   │   │   │   ├── search/
│   │   │   │   ├── categories/
│   │   │   │   ├── products/[id]/
│   │   │   │   ├── shop/[sellerId]/
│   │   │   │   ├── flash/
│   │   │   │   └── coupons/
│   │   │   ├── (shop)/              # 交易
│   │   │   │   ├── layout.tsx
│   │   │   │   ├── cart/
│   │   │   │   ├── checkout/
│   │   │   │   ├── orders/
│   │   │   │   └── pay/
│   │   │   ├── (user)/              # 个人中心
│   │   │   │   ├── layout.tsx
│   │   │   │   ├── profile/
│   │   │   │   ├── addresses/
│   │   │   │   ├── favorites/
│   │   │   │   ├── coupons/
│   │   │   │   ├── points/
│   │   │   │   ├── after-sales/
│   │   │   │   ├── notifications/
│   │   │   │   └── security/
│   │   │   ├── auth/
│   │   │   ├── sign/
│   │   │   ├── ai/
│   │   │   ├── chat/
│   │   │   ├── layout.tsx           # 根 Layout
│   │   │   ├── globals.css
│   │   │   └── providers.tsx        # QueryClient + Auth + Theme Providers
│   │   ├── public/
│   │   └── next.config.ts
│   │
│   ├── admin/                       # 平台管理后台
│   │   ├── app/
│   │   │   ├── layout.tsx           # Ant Design ProLayout
│   │   │   ├── dashboard/
│   │   │   ├── users/
│   │   │   ├── sellers/
│   │   │   ├── products/
│   │   │   ├── orders/
│   │   │   ├── categories/
│   │   │   ├── home-config/
│   │   │   ├── coupons/
│   │   │   ├── flash/
│   │   │   ├── after-sales/
│   │   │   ├── finance/
│   │   │   ├── roles/
│   │   │   └── logs/
│   │   └── next.config.ts
│   │
│   └── seller/                      # 商家后台
│       ├── app/
│       │   ├── layout.tsx           # Ant Design ProLayout
│       │   ├── dashboard/
│       │   ├── products/
│       │   ├── orders/
│       │   ├── shop/
│       │   ├── coupons/
│       │   ├── templates/
│       │   ├── knowledge/
│       │   ├── finance/
│       │   └── apply/
│       └── next.config.ts
│
├── packages/
│   ├── ui/                          # 共享 UI 组件库
│   │   ├── components/
│   │   │   ├── shared/              # 基础组件 (GlassCard, GlassButton, ...)
│   │   │   ├── product/             # 商品组件 (ProductCard, SkuSelector, ...)
│   │   │   ├── cart/                # 购物车组件
│   │   │   ├── order/               # 订单组件
│   │   │   ├── checkout/            # 结算组件
│   │   │   ├── user/                # 用户中心组件
│   │   │   ├── search/              # 搜索组件
│   │   │   ├── marketing/           # 营销组件
│   │   │   ├── chat/                # 消息组件
│   │   │   ├── ai/                  # AI 助手组件
│   │   │   ├── auth/                # 认证组件
│   │   │   └── shop/                # 店铺组件
│   │   └── lib/
│   │       └── utils.ts             # Shadcn/ui cn() 工具
│   │
│   ├── api/                         # API 客户端 + Hooks
│   │   ├── client.ts                # Fetch wrapper
│   │   ├── types/                   # TypeScript 类型定义
│   │   ├── hooks/                   # TanStack Query hooks
│   │   ├── queries/                 # Query key 工厂
│   │   └── mocks/                   # MSW handlers
│   │       ├── handlers/
│   │       ├── fixtures/
│   │       └── server.ts
│   │
│   ├── auth/                        # 认证模块
│   │   ├── middleware.ts            # Next.js Middleware
│   │   └── store.ts                 # Zustand auth store
│   │
│   ├── config/                      # 共享配置
│   │   ├── tailwind.config.ts
│   │   ├── eslint.config.ts
│   │   └── tsconfig.base.json
│   │
│   └── utils/                       # 工具函数
│       ├── format.ts                # 金额/日期格式化
│       ├── validate.ts              # Zod schema
│       └── constants.ts
│
├── pnpm-workspace.yaml
├── package.json
├── turbo.json
└── .github/workflows/
    └── frontend-ci.yml
```

---

## 十、Git 工作流

### 10.1 分支策略

```
main               # 生产就绪
  └── develop       # 集成分支
        ├── feat/phase-1-design-system
        ├── feat/phase-2-brand-home
        ├── feat/phase-3-marketplace
        ├── feat/phase-4-api-integration
        ├── feat/phase-5-perf-testing
        └── feat/phase-6-extensions
```

### 10.2 Commit 规范

```
feat(ui): add GlassCard component
feat(marketplace): implement product search page
fix(cart): handle quantity debounce edge case
perf(image): add blurDataURL for progressive loading
test(e2e): add order placement flow
```

### 10.3 CI/CD Pipeline

```yaml
# .github/workflows/frontend-ci.yml
name: Frontend CI

on:
  pull_request:
    paths: ['front-end/**']

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v2
      - run: pnpm install
      - run: pnpm lint
      - run: pnpm typecheck

  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v2
      - run: pnpm install
      - run: pnpm test
      - run: pnpm test:e2e

  lighthouse:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v2
      - run: pnpm install
      - run: pnpm build
      - uses: treosh/lighthouse-ci-action@v12
        with:
          urls: |
            http://localhost:3000/
            http://localhost:3000/marketplace
            http://localhost:3000/products/1
          budgetPath: .github/lighthouse/budget.json
```

---

## 十一、风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| Three.js 场景性能差 | 中 | 品牌主页卡顿 | 移动端降级静态背景；frameloop="demand"；几何体 < 10k 面 |
| ISR 缓存不一致 | 低 | 商品价格/库存显示过期 | 详情页库存 client fetch；revalidate 保守配置 |
| JWT 刷新竞态条件 | 中 | 多个请求同时 401 | Token 刷新加锁 (Promise cache)；401 请求队列 |
| 移动端适配遗漏 | 中 | 部分页面移动端不可用 | Playwright 移动端 E2E；375px 宽度强制检查 |
| API 类型不同步 | 低 | 运行时类型错误 | CI 中 `openapi-typescript` 自动检查差异；Zod 运行时校验兜底 |
| 购物车乐观更新冲突 | 中 | 数量显示不一致 | 版本号机制；onError 完整 rollback；后台定期全量刷新 |

---

## 十二、完成定义 (Definition of Done)

每个 Phase 完成时必须满足：

- [ ] 代码通过 ESLint + TypeScript strict 检查
- [ ] 相关单元/组件/E2E 测试通过
- [ ] 手动测试: 金路径 (Golden Path) 可走通
- [ ] 响应式: Desktop + Tablet + Mobile 布局正确
- [ ] 可访问性: 键盘导航 + 屏幕阅读器基本可用
- [ ] 性能: 无新增 Lighthouse 退化
- [ ] PR Review: 至少 1 人审核通过
- [ ] 文档: 如有新增组件，更新 §16/§17 对应章节

---

> **前置文档**: [16-Frontend-Product-Design](./16-Frontend-Product-Design.md) · [17-Frontend-Design-System](./17-Frontend-Design-System.md)
>
> **进入代码**: 按 Phase 1 → Phase 6 顺序，每个 Phase 完成后 review 再进入下一 Phase。
