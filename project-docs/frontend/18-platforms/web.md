# 18-platforms/web — Web 平台实施方案

> 归属域: frontend | Owner: doc-engineer | 更新触发条件: Web 平台（含 admin / seller）实施阶段推进时
>
> 上级文档: [18-Frontend-Implementation.md](../18-Frontend-Implementation.md)（总览 —— Phase 模型、平台优先级、跨平台共享物、全局 DoD）
> 平台轴约定见总览 §一；**本文只写 Web 平台独有内容**，不复制总览。

> ⚠️ **可信度边界（必读）**：本文的**工程实践部分**（monorepo、构建、CI/CD、认证中间件机制、Phase 拆解方法）以本平台目标形态为准；
> 其中**页面清单与 API 路径属「设计意图」**，不得当作事实源 ——
> 前端域文档（`15`/`17`/`18`/`16`）均停在 V1.0（2026-08-02），早于契约 V5.7–V5.18 的全部增量。
> **API 与页面映射一律以 [contract/05-API-Specification.md](../../contract/05-API-Specification.md) + 代码为准**
> （依据 [21-Frontend-Refactor-Readiness.md../21-Frontend-Refactor-Readiness.md) §七.3）。

---

## 一、环境准备

### 1.1 工具链

```bash
# 前置条件
Node.js >= 20          # 根 package.json engines 强制
pnpm >= 9              # 根 package.json engines + packageManager: pnpm@9.0.0
```

### 1.2 Monorepo 初始化（从零重建时）

```bash
mkdir front-end && cd front-end
pnpm init
# 配置 pnpm-workspace.yaml + turbo.json

# 创建 apps
mkdir -p apps/web apps/admin apps/seller
mkdir -p packages/ui packages/api packages/auth packages/config packages/utils

# 安装核心依赖
pnpm add -w next@latest react@latest react-dom@latest typescript@latest
pnpm add -w tailwindcss@latest @tailwindcss/postcss
pnpm add -w @tanstack/react-query zustand react-hook-form zod
pnpm add -w lucide-react next-themes
pnpm add -w -D vitest @testing-library/react @playwright/test msw
```

> 现状 `apps/web` 依赖为 `next ^15.0.0` + `react ~18.2.0`（根 `pnpm.overrides` 强制锁 18.2.0）。
> **React 19 升级是独立议题**，见 §八 未决项 —— 不在本文的实施范围内。

### 1.3 本地开发

```bash
cd Implementation/front-end
pnpm dev:web      # Next.js dev，端口 3000
pnpm dev:admin    # 平台管理后台，端口 3001
pnpm dev:seller   # 商家后台，端口 3002
```

### 1.4 后端依赖

Web 平台自身无原生工具链；但联调需要后端基础设施与网关：

```bash
cd Implementation/back-end
docker compose up -d                                    # MySQL + Redis + Nacos
docker compose --profile v1.1 up -d                     # ES + RocketMQ 等扩展件
mvn -pl gateway-service -DskipTests spring-boot:run      # 网关，端口 8080
```

测试分层与执行细节见 [19-Test-Flow-Guide.md](../../quality/19-Test-Flow-Guide.md)。

---

## 二、工程约束

| 约束 | 标准 | 检查工具 |
|------|------|---------|
| TypeScript | strict 模式，禁止 `any` | `tsc --noEmit`（`pnpm type-check`） |
| ESLint | Flat config, `eslint-config-next` | `eslint .`（`pnpm lint`） |
| Prettier | 3.x + `prettier-plugin-tailwindcss` | `prettier --check`（`pnpm format:check`） |
| 测试 | Vitest ≥ 80% 工具函数, Playwright ≥ 5 核心流程 | CI gate |
| 性能 | Lighthouse ≥ 90, LCP < 2.5s | Lighthouse CI |
| 包管理 | pnpm only | `engines` + `packageManager` 字段（`engine-strict` 未显式置位，未核实） |
| 设计 token | 唯一来源 `apps/web/app/globals.css` 的 `@theme`，禁止裸写色值 | `node scripts/check-design-tokens.mjs`（见 §三.3） |
| API 路径 | 禁止硬编码 `/api/` 字面量，走生成客户端 | `grep -rn "'/api/" ... → 0`（基线见 21 §六） |

---

## 三、脚手架与目录

### 3.1 目标目录结构（设计意图）

```
front-end/
├── apps/
│   ├── web/                         # 用户端 H5 + PWA
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
│   │   └── app/                     # Ant Design ProLayout
│   │
│   └── seller/                      # 商家后台
│       └── app/                     # Ant Design ProLayout
│
├── packages/
│   ├── ui/                          # 共享 UI 组件库
│   ├── api/                         # API 客户端 + Hooks
│   ├── auth/                        # 认证模块
│   ├── config/                      # 共享配置
│   └── utils/                       # 工具函数
│
├── pnpm-workspace.yaml
├── package.json
├── turbo.json
└── .github/workflows/
    └── frontend-ci.yml
```

### 3.2 与现状的差异（以代码为准）

> 上表是**目标形态**，不是现状。实测差异如下（取证：[20 §七](../../quality/20-Doc-Code-Consistency-Audit.md)）：

| 项 | 目标（3.1） | 实测现状 | 处置 |
| --- | --- | --- | --- |
| 用户端目录 | `apps/storefront/` | **`apps/web/`** | 目录已改名；`storefront` 不再存在 |
| 路由分组 | `(brand)` / `(marketplace)` / `(shop)` / `(user)` 路由组 | **无路由组**，为平铺真实路径：`marketplace/`、`product/[id]/`、`search/`、`shop/{cart,checkout,orders,pay,after-sale}`、`user/*`（8 页）、`auth/{login,register}`、`stores/` | 目标结构未落地，属待实施项 |
| 品牌主页组件 | `(brand)/_scenes/GardenScene.tsx` | **`app/components/hero-scene.tsx`** 等平铺于 `app/components/` | 同上 |
| 共享组件 | `packages/ui/components/{shared,product,cart,...}/` 分目录 | **`packages/ui/components/*.tsx` 平铺**，kebab-case：`glass-card.tsx`、`product-card.tsx`、`sku-selector.tsx`、`quantity-stepper.tsx`、`search-bar.tsx`、`price-display.tsx` 等 16 文件 | 命名与惯用法见 [08-agents.md](../../collaboration/08-agents.md) §十 |
| 规模 | — | `apps/web` 59 文件 / 5,128 行（约 25 个路由页）；`admin` 9 文件 / 93 行、`seller` 8 文件 / 85 行（**纯骨架**） | `admin`/`seller` 不存在「重构」，只有「从零实现」 |
| API 层 | 从 OpenAPI 生成 | **手写**：`packages/api` 23 文件 / 1,178 行，**仅 24 个 TS 类型**覆盖 286 个端点；无 codegen 配置 | 阶段 1 落地生成式客户端 |

### 3.3 设计 token 落地（Web 侧机制）

Web 是唯一已落 token 层的平台：

| 项 | 值 |
| --- | --- |
| 唯一来源 | `apps/web/app/globals.css` 的 `@theme` 块（Tailwind v4 CSS-first） |
| 文档镜像 | [17-Frontend-Design-System.md](../17-Frontend-Design-System.md) §2.4 |
| 强制比对 | `cd Implementation/front-end && node scripts/check-design-tokens.mjs` |
| 修订顺序 | **先改代码、再改 §2.4** —— 反过来检查失败 |

该脚本的四条规则（死配置 / 文档不撒谎 / 不裸写色值 / 平台覆盖）与其豁免机制见脚本头部注释。
**注意**：`17` 归 frontend-engineer，本文只引用不定义。

---

## 四、Phase 拆解（Web）

> Phase 模型定义见总览 §三。本节只填 Web 平台的具体任务与工期。

| Phase | 主题 | 工期（累计 Day） | 状态 |
| --- | --- | --- | --- |
| Phase 1 | 设计系统落地（Tailwind `@theme` + 基础组件） | Day 1-3 | 🟢 已落地（`apps/web/app/globals.css` + `packages/ui`） |
| Phase 2 | 品牌主页 `/` | Day 4-8 | 🟡 已有组件（`hero-scene` / `brand-philosophy` / `creator-space` / `curated-showcase` / `marketplace-portal` / `parallax-light`），未验收 |
| Phase 3 | 商城核心链路（首页 / 搜索 / 详情 / 购物车 / 结算支付） | Day 9-18 | 🟡 页面骨架在，**API 层 8 组路径为死路径**，链路不可端到端走通 |
| Phase 4 | 接入真实 API（类型生成 → 客户端切换） | Day 19-22 | 🔴 未开始（当前为手写类型） |
| Phase 5 | 性能优化 & 测试（Lighthouse / E2E / PWA） | Day 23-26 | 🔴 未开始 |
| Phase 6 | 扩展（个人中心 / 秒杀 / AI / 消息 / 后台） | Day 27-35+ | 🟡 个人中心页面在；`admin`/`seller` 纯骨架 |

> **工期数字是原始排期，未按现状重估**；重估依赖 21 §二 的契约收敛完成（阻塞项）。

### 4.1 Phase 1 — 设计系统落地

| # | 任务 | 产出 | 优先级 |
|---|------|------|--------|
| P1.1 | Monorepo 骨架搭建 | `pnpm-workspace.yaml`, `turbo.json`, 各 package 的 `package.json` | P0 |
| P1.2 | Tailwind v4 `@theme` 配置 | token 落 `apps/web/app/globals.css` 的 `@theme`（**非** JS config —— Tailwind v4 不自动读 JS config） | P0 |
| P1.3 | 全局 CSS 变量 + 字体加载 | `next/font` 加载 Inter + Noto Sans SC + Playfair Display | P0 |
| P1.4 | GlassCard 组件 | `packages/ui/components/glass-card.tsx` | P0 |
| P1.5 | GlassButton 组件 (4 variants) | 见 `packages/ui` | P0 |
| P1.6 | GlassInput 组件 | 见 `packages/ui` | P1 |
| P1.7 | GlassModal 组件 | 见 `packages/ui` | P1 |
| P1.8 | Skeleton 组件 | shimmer 动画 | P0 |
| P1.9 | EmptyState + ErrorState 组件 | 空态与错误态 | P1 |
| P1.10 | ScrollReveal 组件 | IntersectionObserver（现名 `section-reveal.tsx`） | P1 |
| P1.11 | ImageWithBlur 组件 | 渐进式图片 | P1 |
| P1.12 | 组件 Storybook (可选) | 基础组件可视化验收 | P2 |

> 说明：原清单给的是 `packages/ui/components/shared/GlassCard.tsx` 形式的路径与 PascalCase 文件名，
> **与现状不符**（平铺 + kebab-case）。上表已按现状校正；命名规则见 [08-agents.md](../../collaboration/08-agents.md) §十。

**验收标准**

- [ ] `pnpm dev` 启动 web，看到 Tailwind 样式生效
- [ ] GlassCard 在所有视口正确渲染，hover/active 效果正常
- [ ] GlassButton 4 种 variant 均正确
- [ ] 暗色模式 / 亮色模式 CSS 变量正确切换
- [ ] 字体加载无 FOIT (Flash of Invisible Text)
- [ ] Skeleton shimmer 动画 60fps
- [ ] `node scripts/check-design-tokens.mjs` 通过（R1/R2 无失败）

### 4.2 Phase 2 — 品牌主页

| # | 任务 | 产出 | 优先级 |
|---|------|------|--------|
| P2.1 | 品牌 Layout：透明毛玻璃导航 + 背景 | 品牌主页布局 | P0 |
| P2.2 | HeroSection — Three.js 场景 | Canvas 东方庭院场景 + 昼夜循环 Shader | P0 |
| P2.3 | HeroSection — 视差光照 | 鼠标移动光影跟随（`requestAnimationFrame` + CSS variables） | P1 |
| P2.4 | HeroSection — 中央内容 | 品牌名称 + 理念文字 + 探索商城按钮 | P0 |
| P2.5 | BrandPhilosophy Section | 品牌理念区域：文字 + 留白 + 植物装饰 | P0 |
| P2.6 | FeaturedWorks Section | 精选作品横向滚动（原生 CSS scroll-snap） | P0 |
| P2.7 | CreatorSpace Section | 创作者空间：毛玻璃卡片 + 理念文字 | P1 |
| P2.8 | MarketplaceEntry Section | 进入商城入口：巨型玻璃按钮 + 过渡动画 | P0 |
| P2.9 | ScrollReveal 集成 | 各 Section 滚动渐显 + stagger 延迟 | P1 |
| P2.10 | 响应式适配 | 移动端品牌主页布局 | P1 |

**技术决策 — Three.js 场景**

```tsx
// 使用 @react-three/fiber + @react-three/drei
import { Canvas } from '@react-three/fiber';
import { Environment, OrbitControls } from '@react-three/drei';

// 场景组件 — 仅在客户端加载
const GardenScene = dynamic(() => import('./_scenes/GardenScene'), {
  ssr: false,
  loading: () => <div className="absolute inset-0 bg-ink" />
});
```

**性能要求**

- Canvas 仅渲染背景（不参与页面交互）
- 使用 `frameloop="demand"` 仅在滚动/鼠标移动时渲染
- 几何体面数 < 10k
- Shader 复杂度：仅色温 + 光照方向变化，无后处理
- 移动端降级：静态渐变背景

> 现状：`apps/web/app/components/hero-scene.tsx` 已存在；`@react-three/fiber` **是否已引入未核实**（`apps/web/package.json` 未列该依赖）。

**验收标准**

- [ ] 品牌主页全屏展示，无滚动条异常
- [ ] 滚动白天→黄昏→夜晚过渡平滑 (30fps+)
- [ ] 鼠标视差光照跟随在 60fps
- [ ] "探索商城"按钮跳转到商城入口
- [ ] 移动端降级为静态背景
- [ ] Lighthouse 评分 ≥ 85（品牌主页允许略低于商城）
- [ ] 页面加载时间 < 3s（含 Three.js）

### 4.3 Phase 3 — 商城核心链路

#### 4.3.1 商城首页

| # | 任务 | 产出 | API（**以 contract/05 为准**） |
|---|------|------|-----|
| P3.1 | 商城 Layout | Header + Main + Footer | — |
| P3.2 | Header 导航栏 | Logo + SearchBar + 分类下拉 + 购物车图标 + 用户菜单 | — |
| P3.3 | Footer | 品牌信息 + 链接 + 社交媒体 + 版权 | — |
| P3.4 | Hero Banner | 轮播 Banner（Swiper/Embla） | `GET /api/home/config` |
| P3.5 | 分类入口 | 水平滚动分类图标 | `GET /api/item/category/tree` |
| P3.6 | 精选推荐 | 横向滚动 ProductCard 列表 | `GET /api/item/spu/page?sort=sales` |
| P3.7 | 新品上市 | 2×4 商品网格 | `GET /api/item/spu/page?sort=newest` |
| P3.8 | 活动专区 | 秒杀 + 领券入口卡片 | `GET /api/flash`, `GET /api/coupon/template` |
| P3.9 | ProductCard 组件 | 带 hover 效果的商品卡片 | — |
| P3.10 | ProductGrid 组件 | 响应式商品网格 | — |
| P3.11 | ProductHorizontalScroll 组件 | 横向滚动容器 + 箭头导航 | — |

> ⚠️ 上表 API 列为**设计意图**。**商品 / 评价 / 搜索段的旧路径已按 B4 契约 + B5 代码 + B8 前端同步**（`/api/item/spu/**`、`/api/item/review/spu/**`、`/api/search/spu`）；但仍存在后端无对应的死路径（如 `/api/coupon/available/filter`，全表见 [21 §三](../21-Frontend-Refactor-Readiness.md)）。**落地时逐个以 `contract/05` 校正。**

#### 4.3.2 搜索

| # | 任务 | 产出 | API |
|---|------|------|-----|
| P3.12 | SearchBar 组件 | 搜索框 + 焦点展开 + 建议下拉 | `GET /api/search/suggest` |
| P3.13 | 搜索页 | 搜索结果列表 + 筛选面板 | `GET /api/search/spu` |
| P3.14 | SearchFilter 组件 | 分类/价格/品牌/排序筛选 | — |
| P3.15 | HotKeywords 组件 | 热门搜索标签云 | `GET /api/search/hot` |
| P3.16 | SearchHistory 组件 | 搜索历史（localStorage + API） | 见 contract/05 |

#### 4.3.3 商品详情

| # | 任务 | 产出 | API |
|---|------|------|-----|
| P3.17 | 商品图片轮播 | 多图滑动 + 缩略图导航 + 点击放大（Lightbox） | — |
| P3.18 | 商品信息区 | 名称/价格/标签/优惠券提示 | `GET /api/item/spu/{spuId}/detail` |
| P3.19 | SkuSelector 组件 | 多维度规格选择（颜色/尺寸） | `GET /api/item/spu/{spuId}/skus` |
| P3.20 | QuantityStepper 组件 | 数量加减（min 1, max stock） | — |
| P3.21 | 店铺卡片 | 店铺信息 + 关注 + 进店 | `GET /api/shop/{sellerId}` |
| P3.22 | 商品详情 Tab | 图文详情 / 规格参数 | — |
| P3.23 | 评价摘要 | 评分分布 + 标签 | `GET /api/item/review/spu/{spuId}/summary` |
| P3.24 | 评价列表 | 筛选 + 分页 | `GET /api/item/review/spu/{spuId}/filter` |
| P3.25 | 推荐商品 | "看了又看"横向滚动 | `GET /api/item/spu/page` |
| P3.26 | 加购 / 立即购买 | 按钮交互 + Toast 反馈 | `POST /api/cart/item`，跳转结算 |

#### 4.3.4 购物车

| # | 任务 | 产出 | API |
|---|------|------|-----|
| P3.27 | 购物车页 | 按店铺分组展示 | `GET /api/cart/grouped` |
| P3.28 | CartItem 组件 | 商品行：图片/名称/规格/价格/数量/小计 | — |
| P3.29 | 数量修改 | + / - 按钮 + 输入框（debounced API call） | `PUT /api/cart/item` |
| P3.30 | 选中/取消 + 全选 | Checkbox + 汇总栏联动 | `PATCH /api/cart/item/{skuId}/selected` |
| P3.31 | 删除商品 | 单删 + 批量删 | `DELETE /api/cart/item/{skuId}` |
| P3.32 | 空购物车 | 空状态 + "去逛逛" CTA | — |
| P3.33 | 底部汇总栏 | 合计金额 + 已选数量 + 结算按钮 | — |
| P3.34 | CartDrawer 组件 | 侧边购物车抽屉（悬浮快捷入口） | — |

#### 4.3.5 结算 & 支付

| # | 任务 | 产出 | API |
|---|------|------|-----|
| P3.35 | 结算页 | 地址 + 商品清单 + 优惠券 + 备注 + 金额汇总 | — |
| P3.36 | AddressSelector 组件 | 地址单选列表 + 新增/编辑 inline | `GET/POST /api/user/address/*` |
| P3.37 | CouponSelector 组件 | 可用优惠券列表 + 一键选用 | `GET /api/coupon/available`（**注意**：`/api/coupon/available/filter` 后端不存在） |
| P3.38 | 订单金额汇总 | 商品总额 - 优惠券 = 实付金额（金额一律按「分」整数） | — |
| P3.39 | 提交订单 | 调用 API + loading + 成功跳转支付 | `POST /api/trade/order` |
| P3.40 | 支付页 | 微信二维码 + 支付宝 + 余额支付选择 | `POST /api/pay/order/{orderNo}` |
| P3.41 | 支付状态轮询 | 3s 轮询 + 超时提示 + 成功跳转 | `GET /api/pay/order/{orderNo}/status` |
| P3.42 | 支付结果页 | 成功/失败状态 + 查看订单 + 继续购物 | — |

**验收标准（Phase 3 完成）**

- [ ] 完整交易闭环可走通：浏览 → 搜索 → 详情 → 加购 → 结算 → 支付
- [ ] 所有商品卡片 hover 效果 60fps
- [ ] 搜索响应 < 300ms（含 API）
- [ ] 购物车数量修改 debounce 防抖
- [ ] 结算页地址/优惠券 inline 选择，无页面跳转
- [ ] 移动端适配：所有页面在 375px 宽度正常显示
- [ ] 骨架屏在所有列表页生效

### 4.4 Phase 4 — 接入真实 API

| # | 任务 | 产出 | 优先级 |
|---|------|------|--------|
| P4.1 | API 类型生成 | 从后端 OpenAPI spec 生成 TypeScript 类型到 `packages/api/types/` | P0 |
| P4.2 | Zod Schema 库 | `packages/utils/validate.ts` — 前端校验规则 | P0 |
| P4.3 | Fetch Client | `packages/api/client.ts` — Base URL + 令牌拦截 + 错误处理 | P0 |
| P4.4 | TanStack Query hooks — 商品 | `useProducts`, `useProduct`, `useProductDetail` | P0 |
| P4.5 | TanStack Query hooks — 购物车 | `useCart`, `useAddToCart`, `useUpdateCartItem`, `useRemoveCartItem` | P0 |
| P4.6 | TanStack Query hooks — 订单 | `useOrders`, `useOrder`, `useCreateOrder` | P0 |
| P4.7 | TanStack Query hooks — 支付 | `usePayOrder`, `usePayStatus` | P0 |
| P4.8 | TanStack Query hooks — 搜索 | `useSearch`, `useHotKeywords`, `useSuggestions` | P0 |
| P4.9 | TanStack Query hooks — 用户 | `useUserInfo`, `useAddresses`, `useFavorites`, `useNotifications` | P1 |
| P4.10 | TanStack Query hooks — 营销 | `useCoupons`, `useFlashSales`, `useClaimCoupon`, `useFlashBuy` | P1 |
| P4.11 | TanStack Query hooks — AI | `useAiChat`（Vercel AI SDK `useChat`） | P1 |
| P4.12 | MSW Mock handlers | 为所有 API 提供 mock 数据，前端可脱离后端开发 | P0 |
| P4.13 | Query Key 工厂 | `packages/api/queries/` — 统一 query key 管理 | P0 |
| P4.14 | 乐观更新 | 购物车加减删使用 `onMutate` + `onError` rollback | P1 |

> 现状：`packages/api/hooks/` 已有 15 个 `use-*.ts`（kebab-case，**非**上表的 camelCase 命名），
> `packages/api/queries/` 与 `types/` 已存在。命名以现状为准，见 [08-agents.md](../../collaboration/08-agents.md) §十。

**Fetch Client 实现**

```typescript
// packages/api/client.ts
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

class ApiClient {
  private async request<T>(path: string, options?: RequestInit): Promise<T> {
    const res = await fetch(`${API_BASE}${path}`, {
      ...options,
      credentials: 'include',  // Cookie 携带令牌
      headers: {
        'Content-Type': 'application/json',
        ...options?.headers,
      },
    });

    // 401 → 触发令牌刷新
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

  // put / patch / delete 同构
}
```

> **注意两处风险**（已在 [12-Security-Model.md](../../backend/12-Security-Model.md) 与 21 §四.1 登记，落地前须定案）：
> ① 刷新令牌若存 JS 可读 Cookie，存在 XSS 暴露面 —— 建议改 httpOnly + BFF/Route Handler 代理刷新；
> ② `refreshToken()` 无并发锁，多个请求同时 401 会触发刷新竞态，需 Promise 缓存 + 401 请求队列。

**Query Key 工厂示例**

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

**验收标准**

- [ ] `pnpm dev` 使用 MSW mock，所有页面正常渲染 mock 数据
- [ ] 切换到真实后端（改 env var），核心交易链路正常
- [ ] TanStack Query Devtools 显示请求去重生效
- [ ] 购物车乐观更新：网络慢时 UI 即时响应，失败时 rollback
- [ ] 令牌过期自动刷新，用户无感知
- [ ] `grep -rn "'/api/" Implementation/front-end --include=*.ts --include=*.tsx | wc -l` → **0**（无硬编码路径）

### 4.5 Phase 5 — 性能优化 & 测试

**性能优化**

| # | 任务 | 产出 | 目标 |
|---|------|------|------|
| P5.1 | ISR 配置 | 商品列表 `revalidate=60`, 商品详情 `revalidate=300`, 秒杀 `revalidate=10` | TTFB < 200ms |
| P5.2 | 图片优化 | `next/image` + WebP/AVIF + blurDataURL + 懒加载 | 图片体积 -60% |
| P5.3 | 字体优化 | `next/font` + `subset`（仅中文字符集按需） | FOIT 0ms |
| P5.4 | Bundle 分析 | `@next/bundle-analyzer` — 识别大 chunk | 首屏 JS < 100KB |
| P5.5 | 代码分割 | `dynamic(() => import(...))` + `suspense` | 非首屏组件按需加载 |
| P5.6 | Link prefetch | 视口内 `<Link>` 自动 prefetch | 页面切换 < 100ms |
| P5.7 | 动画审查 | 检查所有动画是否仅用 transform/opacity | 60fps |
| P5.8 | Lighthouse CI | 配置 GitHub Actions 门禁 | 评分 ≥ 90 |

**测试**

| # | 任务 | 工具 | 覆盖 |
|---|------|------|------|
| P5.9 | 工具函数单测 | Vitest | `formatPrice`, Zod schemas, query keys |
| P5.10 | 组件测试 | Vitest + RTL | GlassCard, GlassButton, ProductCard, SkuSelector, QuantityStepper |
| P5.11 | 购物车逻辑测试 | Vitest | 加购/去重/选中/全选/计算总价 |
| P5.12 | E2E — 浏览商品 | Playwright | 首页→分类→详情, 验证 LCP < 2.5s |
| P5.13 | E2E — 下单支付 | Playwright + MSW | 登录→加购→结算→提交→支付成功 |
| P5.14 | E2E — 秒杀 | Playwright + MSW | 秒杀页→抢购→售罄/成功 |
| P5.15 | E2E — 移动端视口 | Playwright（iPhone 14 viewport） | 移动端适配 + Touch 交互 |
| P5.16 | 视觉回归（可选） | Percy / Chromatic | 卡片/按钮组件视觉一致性 |

> Web 的浏览器/视口矩阵、设备矩阵与各平台测试执行流程见 [11-Test-Strategy.md](../../quality/11-Test-Strategy.md) 与 [19-Test-Flow-Guide.md](../../quality/19-Test-Flow-Guide.md)（**唯一事实来源，本文不复制**）。

**PWA 配置**

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

> PWA 是本平台**替代原生 App 的第一手段**（激活 `apps/mobile` 占位工程的触发条件见 `Implementation/front-end/apps/mobile/README.md`）。
> 离线缓存策略与令牌刷新必须对齐：缓存的 `NetworkFirst` API 响应不得含鉴权数据。

**验收标准**

- [ ] Lighthouse Desktop ≥ 95, Mobile ≥ 90
- [ ] LCP < 2.5s, INP < 200ms, CLS < 0.1
- [ ] 所有 E2E 测试通过
- [ ] PWA 可安装，离线可查看缓存的商品页
- [ ] CI 门禁：lint + typecheck + test + Lighthouse

### 4.6 Phase 6 — 扩展功能

#### 4.6.1 个人中心

| 模块 | 页面 | API 依赖（**以 contract/05 为准**） |
|------|------|---------|
| 认证 | 登录/注册/找回密码 | 见 contract/05（**`POST /api/auth/login` 已确认为死路径**，真实为 `/api/auth/web/login`） |
| 资料 | 个人中心 + 编辑 + 头像上传 | `GET /api/user/info`, `PATCH /api/user/profile`, `POST /api/user/profile/avatar` |
| 地址 | 地址列表 + 新增/编辑/删除/设默认 | `GET/POST/PUT/DELETE /api/user/address/*` |
| 订单 | 订单列表 + 详情 + 取消 + 确认收货 | `GET /api/trade/order/page`, `/{orderNo}`, `/cancel`, `/confirm` |
| 收藏 | 收藏列表 + 取消收藏 | `GET/DELETE /api/user/favorite` |
| 优惠券 | 可用/已用/已过期 Tab | `GET /api/coupon/available`, `/used` |
| 积分 | 积分余额 + 明细 | **`GET /api/points/balance`, `/api/points/history`**（非 `/api/user/points/*`） |
| 签到 | 签到日历 + 连续签到 | **`POST /api/sign`, `GET /api/sign/status`**（非 `/api/user/sign*`） |
| 售后 | 申请 + 列表 | `POST /api/after-sale`, `GET /api/after-sale` |
| 钱包 | 余额 + 充值 | **`GET /api/wallet`, `POST /api/wallet/recharge`**（非 `/api/user/balance*`） |
| 消息 | 通知列表 + 已读 | **`GET /api/notifications*`**（非 `/api/user/notifications*`） |

#### 4.6.2 秒杀频道

| # | 任务 | API |
|---|------|-----|
| P6.1 | 秒杀频道页 | `GET /api/flash`, `GET /api/flash/timeline` |
| P6.2 | FlashSaleCard 组件 | 倒计时 + 进度条 + 抢购按钮 |
| P6.3 | FlashCountdown 组件 | 服务端时间校准 + 毫秒级倒计时 |
| P6.4 | FlashProgress 组件 | 库存进度条 |
| P6.5 | 抢购交互 | `POST /api/flash/buy` → loading → 成功/失败/售罄 |

#### 4.6.3 AI 助手 & 消息

| # | 任务 | API |
|---|------|-----|
| P6.6 | AiChatPanel 组件 | `POST /api/ai/chat/stream`（SSE + Vercel AI SDK `useChat`） |
| P6.7 | AiProductRecommend 组件 | AI 推荐商品卡片渲染 |
| P6.8 | AiConversationHistory 组件 | `GET /api/ai/conversations` 会话列表 |
| P6.9 | ChatWindow 组件 | WebSocket STOMP `/ws/chat` |
| P6.10 | ConversationList 组件 | `GET /api/chat/conversations` |

#### 4.6.4 商家后台 + 管理后台

| 模块 | 关键页面 | 技术 |
|------|---------|------|
| 商家后台 | 仪表盘、商品管理(ProTable)、订单管理、发货、优惠券、结算、店铺设置 | Ant Design 5 + ProComponents |
| 管理后台 | 数据仪表盘、用户管理、商家审核、商品管理、订单管理、分类管理、首页装修、优惠券管理、秒杀管理、财务管理、角色权限、操作日志 | Ant Design 5 + ProComponents |

```bash
pnpm add antd @ant-design/pro-components -F @icedmall/admin
pnpm add antd @ant-design/pro-components -F @icedmall/seller
```

后台页面是**纯客户端渲染（SPA 模式）**，不需要 SEO。使用 Ant Design ProLayout 开箱即用的侧边栏 + 面包屑 + 权限路由。

> 现状：`apps/admin`（9 文件 / 93 行）与 `apps/seller`（8 文件 / 85 行）为**纯骨架**，
> 不具备「重构」基础，按从零实现组织任务。

---

## 五、认证实施（Web 侧落地）

> 认证**语义**是跨平台统一的（见总览 §五.3）；本节只写 Web 的落地机制。

### 5.1 认证流程

```
1. 用户提交登录表单
2. 登录接口 → 后端 Set-Cookie: access_token (httpOnly)
3. Next.js Middleware 拦截需要认证的路由
4. Middleware 验证 access_token (RS256 公钥)
   ├── 有效 → 放行, headers 附加 userId/username
   ├── 过期 → 尝试 refresh_token → 新 access_token
   └── 失败 → 302 redirect /auth/login
5. 前端不存储 Token, 不操作 Cookie, 全由 httpOnly Cookie 管理
```

> ⚠️ 第 5 步与**现状不符**：实测 `packages/api/token.ts` 把 `access_token` 与 7 天有效期的 `refresh_token`
> **都放在 JS 可读 Cookie**，该文件自注存在 XSS 暴露面（取证：[20 §七](../../quality/20-Doc-Code-Consistency-Audit.md)）。
> 目标形态与现状的差距是**阶段 2 的阻塞决策**（[21 §四.1../21-Frontend-Refactor-Readiness.md)），落地前必须定案。

### 5.2 Middleware 实现

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
      // Token 过期或无效 → 尝试刷新
      const refreshToken = request.cookies.get('refresh_token')?.value;
      if (refreshToken) {
        const refreshRes = await fetch(`${request.nextUrl.origin}/api/auth/refresh?refresh_token=${refreshToken}`, {
          method: 'POST',
        });
        if (refreshRes.ok) {
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

> 两点提醒：① 上述 `PROTECTED_PATHS` 与现状路由（`shop/cart`、`shop/checkout`…）**不一致**，需按 §3.2 校正；
> ② 通过 URL query 传 `refresh_token` 会进访问日志与 Referer，属反模式 —— 正式实现应改为 BFF Route Handler 读取 httpOnly Cookie。

### 5.3 Zustand Auth Store

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

> 文件名与现状一致（`packages/auth/store.ts` 已存在）；但 `logout` 的路径需按 contract/05 校正。

---

## 六、CI/CD

```yaml
# .github/workflows/frontend-ci.yml
name: Frontend CI

on:
  pull_request:
    paths: ['Implementation/front-end/**']

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v2
      - run: pnpm install
      - run: pnpm lint
      - run: pnpm type-check

  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v2
      - run: pnpm install
      - run: pnpm test
      - run: pnpm test:e2e

  tokens:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: node Implementation/front-end/scripts/check-design-tokens.mjs

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
            http://localhost:3000/product/1
          budgetPath: .github/lighthouse/budget.json
```

> 状态：`paths` 已按现状改为 `Implementation/front-end/**`（原文写 `front-end/**`，实际根目录是 `Implementation/front-end/`）；
> `tokens` 任务对应 `17 §2.4` 的 token 强制比对（脚本已存在），**是否已接入 `.github/workflows/ci.yml` 未核实**。
> 后端 CI（`.github/workflows/ci.yml`）是既有事实，前端 CI 与之独立。

---

## 七、发布、灰度与回滚（Web 平台）

Web 是**唯一可以随时回滚**的平台 —— 这是它与应用商店分发型平台最大的差异：

| 维度 | Web 能力 |
| --- | --- |
| 构建产物 | Next.js 构建输出（`.next`），容器镜像 |
| 制品库 | 镜像仓库（现状与部署拓扑详见 [15 §8.7 部署架构](../15-Front-End-Technology-Selection.md)） |
| 发布 | 滚动更新 / 蓝绿；无需任何审核 |
| 灰度 | 按流量比例或用户分桶；**随时可调** |
| 回滚 | **回退镜像版本即可，分钟级生效** |
| 数据兼容 | 上新版本时必须同时兼容旧前端（客户端缓存与 PWA 会滞留旧版本） |
| 强制更新 | 无。Service Worker 需 `skipWaiting` + 版本失效策略推动刷新 |

**平台专属 DoD（叠加在总览 §七 的全局 DoD 之上）**

- [ ] `pnpm lint` + `pnpm type-check` + `pnpm test` 全绿
- [ ] Lighthouse Desktop ≥ 95 / Mobile ≥ 90（品牌主页 ≥ 85）
- [ ] `node scripts/check-design-tokens.mjs` 通过
- [ ] 前端无硬编码 `/api/` 路径（基线 2 见 [21 §六../21-Frontend-Refactor-Readiness.md)）
- [ ] PWA 可安装、离线可访问缓存商品页，且缓存不含鉴权数据
- [ ] `admin` / `seller` 后台关键路径人工验收通过
- [ ] 回滚演练：镜像回退后核心链路可用

---

## 八、风险与未决项

### 8.1 Web 平台风险

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| Three.js 场景性能差 | 中 | 品牌主页卡顿 | 移动端降级静态背景；`frameloop="demand"`；几何体 < 10k 面 |
| ISR 缓存不一致 | 低 | 商品价格/库存显示过期 | 详情页库存 client fetch；`revalidate` 保守配置 |
| 令牌刷新竞态 | 中 | 多个请求同时 401 | 刷新加锁（Promise cache）+ 401 请求队列 |
| 移动端适配遗漏 | 中 | 部分页面移动端不可用 | 移动端视口 E2E；375px 宽度强制检查 |
| API 类型不同步 | 高 | 运行时类型错误 | **改为从 OpenAPI 生成**（阶段 1）；Zod 运行时校验兜底 |
| 购物车乐观更新冲突 | 中 | 数量显示不一致 | 版本号机制；`onError` 完整 rollback；后台定期全量刷新 |
| PWA 缓存与发布冲突 | 中 | 用户滞留旧版本 | Service Worker 版本失效策略；关键接口 `NetworkFirst` |

### 8.2 未决项（阻塞或需人决策，**不是本文能定的**）

| # | 事项 | 归属 | 说明 |
| --- | --- | --- | --- |
| 1 | 刷新令牌存储方案（现状 JS 可读 Cookie vs httpOnly + BFF） | **前端架构决策 → frontend-engineer**（`15`/ADR） | 决定整个认证架构与 `middleware.ts` 实现 |
| 2 | 契约 → TS 类型生成方案（手写 vs orval/openapi-typescript） | **前端架构决策 → frontend-engineer** | 手写是 8 组死路径的根因 |
| 3 | React 版本（`15` 声明 19 / 代码锁 18.2.0） | **frontend-engineer**（属 `15` 的选型决策） | 二选一，不能两边都留 |
| 4 | `admin` / `seller` 是否维持独立 Next.js 应用 | **前端架构决策 → frontend-engineer** | 现状 5 app monorepo 已成型 |
| 5 | 契约收敛（33 条计划中端点移出、13 条更正、`/api/verification/codes` 补录） | **product-manager**（`contract/05`） | **阶段 4/6 的硬前置**，不做完本文所有 API 路径都无法校正 |

---

> **上级文档**: [18-Frontend-Implementation.md](../18-Frontend-Implementation.md)（总览）
> **平台轴同层**: [desktop](./desktop.md) · [android](./android.md) · [ios](./ios.md) · [miniprogram](./miniprogram.md)
> **上游依据**: [15-Front-End-Technology-Selection.md](../15-Front-End-Technology-Selection.md)（选型）· [17-Frontend-Design-System.md](../17-Frontend-Design-System.md)（设计系统）· [21-Frontend-Refactor-Readiness.md../21-Frontend-Refactor-Readiness.md)（准入基线）
> **进度**: Phase 1 已落地；Phase 2/3 骨架在但 API 层需重写；Phase 4-6 未开始。
