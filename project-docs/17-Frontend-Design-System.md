# 17 — 前端设计系统文档

> 最后更新: 2026-08-02 | 版本: V1.0
>
> 依赖: [16-Frontend-Product-Design](./16-Frontend-Product-Design.md) · [15-Front-End-Technology-Selection](./15-Front-End-Technology-Selection.md) · [prompt.md](./prompt.md)
>
> 下一文档: [18-Frontend-Implementation](./18-Frontend-Implementation.md)

---

## 一、视觉世界观

### 1.1 设计语言: 东方自然主义 × 未来玻璃艺术

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│   核心理念:                                                  │
│                                                             │
│   Apple 的空间感 (Depth & Layering)                          │
│        +                                                    │
│   日本庭院的留白 (Ma — 間)                                    │
│        +                                                    │
│   高级生活方式品牌 (Premium Lifestyle)                        │
│        +                                                    │
│   未来科技感 (Future Tech — subtle, not loud)                │
│                                                             │
│   视觉关键词:                                                │
│   雾白 · 深墨 · 暖灰 · 青绿 · 金微光 · 夜蓝紫                │
│   毛玻璃 · 柔光 · 景深 · 留白 · 呼吸                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 材质系统

| 材质 | CSS 实现 | 使用场景 |
|------|---------|---------|
| **毛玻璃 (Frosted Glass)** | `backdrop-filter: blur(20px) saturate(180%)` + `bg-white/70` | 导航栏、卡片、弹窗、Hero 叠加层 |
| **柔光 (Soft Glow)** | `box-shadow: 0 0 80px rgba(180,200,180,0.15)` | 品牌主页背景、卡片悬停 |
| **渐变阴影** | `box-shadow: 0 4px 24px -8px rgba(0,0,0,0.08), 0 0 0 1px rgba(0,0,0,0.04)` | 卡片浮起效果 |
| **透明层次** | 多层半透明叠加 (`bg-white/40` + `bg-white/60` + `bg-white/80`) | 前景/中景/背景分离 |
| **微光** | `background: radial-gradient(ellipse at center, rgba(200,220,200,0.3), transparent)` | 按钮高光、卡片顶部光源 |

---

## 二、色彩系统

### 2.1 主色调

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│   雾白 (Mist White)          深墨黑 (Ink Black)              │
│   #FAFAF8                    #1A1A1A                        │
│   bg-primary                 text-primary                    │
│   背景主色                    文字主色                         │
│                                                             │
│   暖灰 (Warm Gray)           冷灰 (Cool Gray)                │
│   #F5F3F0                    #F0F0F2                         │
│   bg-secondary               bg-tertiary                     │
│   卡片背景                    分隔/禁用背景                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 点缀色

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│   青绿 (Cyan Green)          金微光 (Gold Shimmer)            │
│   #2D8B6E                    #C8A96E                         │
│   accent-primary             accent-luxury                   │
│   主按钮/链接/选中态           VIP标签/高亮点缀                 │
│                                                             │
│   夜蓝紫 (Night Violet)      暖琥珀 (Warm Amber)              │
│   #4A4A7A                    #D4956A                         │
│   accent-deep                accent-warm                     │
│   深色模式点缀/强调文字        促销/秒杀标签 (慎用)              │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 2.3 语义色

| Token | HEX | 用途 |
|-------|-----|------|
| `success` | `#2D8B6E` | 成功状态、库存充足 |
| `warning` | `#D4956A` | 库存紧张、即将过期 |
| `error` | `#C45A5A` | 错误、售罄、支付失败 |
| `info` | `#6B8AAB` | 信息提示 |

### 2.4 Tailwind CSS v4 Design Tokens

```css
/* globals.css — Tailwind CSS v4 @theme */
@theme {
  /* 主色调 */
  --color-mist: #FAFAF8;
  --color-ink: #1A1A1A;
  --color-warm-gray: #F5F3F0;
  --color-cool-gray: #F0F0F2;

  /* 文字 */
  --color-text-primary: #1A1A1A;
  --color-text-secondary: #6B6B6B;
  --color-text-tertiary: #9B9B9B;
  --color-text-disabled: #C0C0C0;
  --color-text-inverse: #FAFAF8;

  /* 点缀色 */
  --color-accent: #2D8B6E;
  --color-accent-hover: #247A5E;
  --color-accent-light: #E8F5F0;
  --color-gold: #C8A96E;
  --color-gold-light: #F5F0E5;
  --color-violet: #4A4A7A;
  --color-amber: #D4956A;

  /* 玻璃效果 */
  --glass-blur: 20px;
  --glass-saturate: 180%;
  --glass-bg: rgba(255, 255, 255, 0.70);
  --glass-bg-light: rgba(255, 255, 255, 0.40);
  --glass-bg-heavy: rgba(255, 255, 255, 0.85);
  --glass-border: rgba(255, 255, 255, 0.30);
  --glass-shadow: 0 8px 32px rgba(0, 0, 0, 0.06);

  /* 阴影 */
  --shadow-card: 0 1px 3px rgba(0,0,0,0.04), 0 1px 2px rgba(0,0,0,0.06);
  --shadow-card-hover: 0 4px 24px -8px rgba(0,0,0,0.08), 0 0 0 1px rgba(0,0,0,0.04);
  --shadow-glass: 0 8px 32px rgba(0, 0, 0, 0.06);
  --shadow-button: 0 2px 8px rgba(45, 139, 110, 0.25);

  /* 圆角 */
  --radius-sm: 6px;
  --radius-md: 10px;
  --radius-lg: 16px;
  --radius-xl: 24px;
  --radius-full: 9999px;

  /* 间距 */
  --spacing-section: 80px;
  --spacing-section-mobile: 48px;

  /* 字体 */
  --font-sans: 'Inter', 'Noto Sans SC', system-ui, sans-serif;
  --font-display: 'Playfair Display', 'Noto Serif SC', serif;
  --font-mono: 'JetBrains Mono', 'Fira Code', monospace;
}
```

### 2.5 品牌主页专属配色

品牌主页（`/`）使用暗色调为主的变体：

| Token | 品牌主页 | 商城 |
|-------|---------|------|
| 背景 | 深墨黑 `#1A1A1A` | 雾白 `#FAFAF8` |
| 卡片 | 半透明深色玻璃 | 暖灰 + 毛玻璃 |
| 文字 | 雾白 | 深墨黑 |
| 点缀 | 金微光 + 夜蓝紫 | 青绿 + 暖琥珀 |

---

## 三、字体系统

### 3.1 字体栈

```css
/* 正文 — Inter (拉丁) + Noto Sans SC (中文) */
--font-sans: 'Inter', 'Noto Sans SC', system-ui, -apple-system, sans-serif;

/* 展示 — Playfair Display (拉丁) + Noto Serif SC (中文) */
--font-display: 'Playfair Display', 'Noto Serif SC', Georgia, serif;

/* 等宽 — JetBrains Mono (价格/数字) */
--font-mono: 'JetBrains Mono', 'Fira Code', 'Courier New', monospace;
```

### 3.2 字号阶梯 (Type Scale)

| Token | Size | Line Height | Weight | 用途 |
|-------|------|-------------|--------|------|
| `text-2xs` | 10px | 14px | 400 | 极小标签、角标 |
| `text-xs` | 12px | 16px | 400/500 | 辅助信息、时间戳 |
| `text-sm` | 14px | 20px | 400/500 | 正文、表单标签 |
| `text-base` | 16px | 24px | 400/500/600 | 标准正文 |
| `text-lg` | 18px | 28px | 500/600 | 强调段落、卡片标题 |
| `text-xl` | 20px | 30px | 600 | 小标题 |
| `text-2xl` | 24px | 32px | 600/700 | 区块标题 |
| `text-3xl` | 30px | 38px | 700 | 页面标题 |
| `text-4xl` | 36px | 44px | 700 | Hero 副标题 |
| `text-5xl` | 48px | 56px | 700/800 | Hero 主标题 |
| `text-6xl` | 60px | 68px | 800 | 品牌名称 (品牌主页) |

### 3.3 价格数字专用

```css
.price {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;        /* 等宽数字，防止跳动 */
  letter-spacing: -0.02em;                    /* 稍微收紧 */
}

.price-large {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
}

.price-small {
  font-size: 14px;
  font-weight: 600;
}
```

---

## 四、组件视觉规范

### 4.1 GlassCard — 毛玻璃卡片

```
┌──────────────────────────────────────────────┐
│                                              │
│  bg: rgba(255,255,255, 0.70)                 │
│  backdrop-filter: blur(20px) saturate(180%)  │
│  border: 1px solid rgba(255,255,255, 0.30)  │
│  border-radius: 16px                         │
│  box-shadow: 0 8px 32px rgba(0,0,0, 0.06)   │
│                                              │
│  Hover:                                       │
│  bg: rgba(255,255,255, 0.85)                 │
│  transform: translateY(-2px)                 │
│  box-shadow: 0 12px 40px rgba(0,0,0, 0.10)  │
│  transition: all 300ms ease-out              │
│                                              │
│  ┌──────────────────────────────────────┐    │
│  │                                      │    │
│  │   内容区                              │    │
│  │                                      │    │
│  └──────────────────────────────────────┘    │
│                                              │
└──────────────────────────────────────────────┘
```

**Tailwind 实现：**

```tsx
// components/shared/GlassCard.tsx
export function GlassCard({ children, className, ...props }: GlassCardProps) {
  return (
    <div
      className={cn(
        // 玻璃材质
        'bg-white/70 backdrop-blur-[20px] backdrop-saturate-[180%]',
        'border border-white/30',
        'rounded-2xl',
        'shadow-[0_8px_32px_rgba(0,0,0,0.06)]',
        // 交互
        'transition-all duration-300 ease-out',
        'hover:bg-white/85 hover:-translate-y-0.5',
        'hover:shadow-[0_12px_40px_rgba(0,0,0,0.10)]',
        className
      )}
      {...props}
    >
      {children}
    </div>
  );
}
```

### 4.2 ProductCard — 商品卡片

```
┌──────────────────────────────────────────────┐
│                                              │
│  ┌──────────────────────────────────────┐    │
│  │                                      │    │
│  │         商品图片                      │    │
│  │         (aspect-[4/5])               │    │
│  │         object-cover                  │    │
│  │                                      │    │
│  │  ┌─────┐                             │    │
│  │  │热销 │  ← 左上角标签                 │    │
│  │  └─────┘                             │    │
│  │                                      │    │
│  │              ♡  ← 右上角收藏按钮       │    │
│  │                                      │    │
│  └──────────────────────────────────────┘    │
│                                              │
│  商品名称 (1行, 超出省略)                      │
│  一句卖点 (1行, text-secondary, 14px)         │
│                                              │
│  ¥128.00  ¥158.00                            │
│  到手价    原价(删除线)                        │
│                                              │
│  ★ 4.7  |  已售 1280                          │
│                                              │
└──────────────────────────────────────────────┘

悬停效果:
  - 图片 scale(1.03) + 柔光
  - 卡片浮起 (translateY -4px + 阴影增强)
  - 显示快捷加购按钮 (从底部滑入)
  - transition: 300ms ease-out
```

**Tailwind 实现：**

```tsx
// components/product/ProductCard.tsx
export function ProductCard({ product }: { product: ProductVO }) {
  return (
    <GlassCard className="group overflow-hidden cursor-pointer">
      {/* 图片区 */}
      <div className="relative aspect-[4/5] overflow-hidden">
        <Image
          src={product.mainImage}
          alt={product.name}
          fill
          className="object-cover transition-transform duration-500 ease-out group-hover:scale-[1.03]"
          sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 25vw"
        />

        {/* 左上: 标签 */}
        {product.salesTags?.map(tag => (
          <span key={tag} className="absolute top-3 left-3 px-2 py-0.5
            bg-accent/90 backdrop-blur-sm text-white text-xs rounded-full">
            {tag}
          </span>
        ))}

        {/* 右上: 收藏 */}
        <button className="absolute top-3 right-3 w-8 h-8
          flex items-center justify-center
          bg-white/60 backdrop-blur-sm rounded-full
          opacity-0 group-hover:opacity-100 transition-opacity duration-200
          hover:bg-white/90">
          <Heart size={16} />
        </button>

        {/* 底部: 悬浮加购按钮 */}
        <div className="absolute bottom-0 left-0 right-0 p-3
          translate-y-full group-hover:translate-y-0
          transition-transform duration-300 ease-out">
          <button className="w-full py-2.5 bg-accent text-white text-sm font-medium
            rounded-lg shadow-button hover:bg-accent-hover
            transition-colors duration-200">
            加入购物车
          </button>
        </div>
      </div>

      {/* 信息区 */}
      <div className="p-4 space-y-1.5">
        <h3 className="text-sm font-medium text-text-primary line-clamp-1">
          {product.name}
        </h3>

        {/* 价格行 */}
        <div className="flex items-baseline gap-2">
          <span className="text-lg font-bold text-ink font-mono">
            ¥{(product.skus?.[0]?.price ?? 0) / 100}
          </span>
          {product.skus?.[0]?.originalPrice && (
            <span className="text-xs text-text-tertiary line-through font-mono">
              ¥{product.skus[0].originalPrice / 100}
            </span>
          )}
        </div>

        {/* 评价+销量 */}
        <div className="flex items-center gap-3 text-xs text-text-tertiary">
          {product.rating && (
            <span className="flex items-center gap-1">
              <Star size={12} className="text-amber" fill="currentColor" />
              {product.rating}
            </span>
          )}
          <span>已售 {product.soldCount}</span>
        </div>
      </div>
    </GlassCard>
  );
}
```

### 4.3 ProductGrid — 商品网格

```
Desktop (>1024px):  4列
Tablet (640-1024px): 2列
Mobile (<640px):    2列 (更窄卡片)

间距: gap-4 (mobile) / gap-6 (desktop)
```

### 4.4 商品详情 SKU 选择器

```
┌──────────────────────────────────────────────┐
│  颜色:                                        │
│  ┌──────┐  ┌──────┐  ┌──────┐               │
│  │ 天青 │  │ 月白 │  │ 墨黑 │               │
│  │ ✓    │  │      │  │      │               │
│  └──────┘  └──────┘  └──────┘               │
│  选中: ring-2 ring-accent bg-accent-light     │
│  未选中: bg-warm-gray hover:bg-cool-gray      │
│  不可选: opacity-40 cursor-not-allowed       │
│                                              │
│  尺寸:                                        │
│  ┌──────┐  ┌──────┐                         │
│  │ 标准 │  │ 加大 │                         │
│  └──────┘  └──────┘                         │
└──────────────────────────────────────────────┘
```

### 4.5 GlassButton — 玻璃按钮

```
Variants:

1. Primary (实心):
   bg-accent text-white
   hover: bg-accent-hover shadow-button
   active: scale-[0.98]

2. Glass (毛玻璃):
   bg-white/40 backdrop-blur-md border border-white/30
   hover: bg-white/60 shadow-glass
   active: scale-[0.98]

3. Ghost (透明):
   bg-transparent
   hover: bg-accent-light text-accent

4. Luxury (金边):
   bg-transparent border border-gold/40 text-gold
   hover: bg-gold-light border-gold

Size:
   sm: h-8 px-4 text-xs rounded-md
   md: h-10 px-6 text-sm rounded-lg
   lg: h-12 px-8 text-base rounded-xl
   xl: h-14 px-10 text-lg rounded-xl   (品牌主页 CTA)
```

### 4.6 搜索框

```
导航栏状态一: 收缩图标（默认）
┌──────────────────────────────────────────────┐
│  [🔍]                                        │
│  点击 → 丝滑展开 (width 0→320px, 500ms)      │
└──────────────────────────────────────────────┘

导航栏状态二: 展开搜索栏
┌──────────────────────────────────────────────────────┐
│  🔍  [ 🔍 手工陶瓷（轮播占位词，每3s切换）]      [🔍]  │
│  bg-white/90 backdrop-blur-xl border-accent/40      │
│  rounded-full shadow-lg                              │
│  Enter/点击搜索按钮 → 提交; Esc → 收起                │
└──────────────────────────────────────────────────────┘

搜索页完整搜索框:
┌──────────────────────────────────────────────────────┐
│  🔍  [  placeholder 轮播热点词  ]              [搜索]│
│                                                      │
│  bg-white/60 backdrop-blur-md                        │
│  border border-white/40                              │
│  rounded-xl h-11                                     │
│                                                      │
│  focus-within:                                        │
│  bg-white/90 border-accent shadow-[0_0_0_3px]       │
│  shadow-accent/20                                    │
│                                                      │
│  下拉建议面板:                                        │
│  ┌────────────────────────────────────────────┐      │
│  │  搜索历史                          [清空]   │      │
│  │  · 手工陶瓷茶杯                             │      │
│  │  · 紫砂壶                                   │      │
│  │  ─────────────────────────────              │      │
│  │  热门搜索                                   │      │
│  │  · 茶具  · 陶瓷  · 咖啡杯  · 香薰          │      │
│  └────────────────────────────────────────────┘      │
│  点击历史词/热门词 → 直接提交搜索                       │
└──────────────────────────────────────────────────────┘
```

### 4.7 导航栏

```
┌──────────────────────────────────────────────────────┐
│ [🍃]  iceMall    搜索框...      分类▾  🛒(3)  👤     │
│                                                      │
│  position: sticky; top: 0; z-50                      │
│  bg-white/70 backdrop-blur-[20px] backdrop-saturate-[180%]│
│  border-b border-white/20                            │
│  h-16 px-6                                           │
│                                                      │
│  滚动后增加: border-b border-cool-gray/50            │
│  transition: border-color 300ms                      │
└──────────────────────────────────────────────────────┘
```

### 4.8 品牌主页 Hero

```
┌──────────────────────────────────────────────────────┐
│                                                      │
│  min-height: 100vh                                    │
│  background: 动态 Canvas/Three.js 场景                 │
│              (东方庭院 + 昼夜循环)                      │
│                                                      │
│  overlay:                                             │
│  background: radial-gradient(                         │
│    ellipse at center,                                 │
│    transparent 40%,                                   │
│    rgba(26,26,26,0.4) 100%                           │
│  )                                                    │
│                                                      │
│  中央内容:                                            │
│  ┌──────────────────────────────────┐               │
│  │                                  │               │
│  │       冰 美 商 城                 │               │
│  │       (font-display, 60px)       │               │
│  │                                  │               │
│  │    "科技，自然融入生活"             │               │
│  │    (text-xl, text-white/70)      │               │
│  │                                  │               │
│  │    ┌──────────────────┐          │               │
│  │    │   探 索 商 城     │          │               │
│  │    └──────────────────┘          │               │
│  │    GlassButton (xl, luxury)      │               │
│  │                                  │               │
│  └──────────────────────────────────┘               │
│                                                      │
│  底部指示: ↓ 向下滚动 (呼吸动画)                       │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### 4.9 优惠券卡片

```
┌──────────────────────────────────────────────┐
│                                              │
│  ┌────────────┬─────────────────────────┐    │
│  │            │                         │    │
│  │   ¥20      │  满200减20               │    │
│  │   (金额)   │  全场通用                │    │
│  │            │  2026.06.01 - 06.18     │    │
│  │            │                         │    │
│  │            │  已领取 65%             │    │
│  │            │  ████████░░░░ 进度条     │    │
│  │            │                         │    │
│  │            │       [立即领取]         │    │
│  │            │                         │    │
│  └────────────┴─────────────────────────┘    │
│                                              │
│  左侧: bg-accent-light, 金额大字               │
│  右侧: bg-white, 规则说明 + 领取按钮            │
│  圆角分割线: 半圆缺口 (clip-path)              │
│                                              │
└──────────────────────────────────────────────┘
```

### 4.10 秒杀卡片

```
┌──────────────────────────────────────────────┐
│                                              │
│  ┌──────────────────────────────────────┐    │
│  │  ⚡ 限时秒杀         距结束 02:30:15  │    │
│  └──────────────────────────────────────┘    │
│                                              │
│  [商品图]  商品名称                           │
│            ¥99.00  ¥128.00                   │
│            秒杀价   原价                       │
│            ████████░░  已抢 72%              │
│            [立即抢购]                         │
│                                              │
└──────────────────────────────────────────────┘
```

---

## 五、动效规范

### 5.1 动效原则

| 原则 | 说明 | 实现 |
|------|------|------|
| **GPU 友好** | 只用 `transform` + `opacity` 做动画 | 禁止 `width`/`height`/`top`/`left` 动画 |
| **60fps** | 所有动画必须平滑 | `will-change` + `transform: translateZ(0)` |
| **自然缓动** | 不对称缓动模拟物理运动 | `cubic-bezier(0.4, 0, 0.2, 1)` 标准; `cubic-bezier(0, 0, 0.2, 1)` 入场; `cubic-bezier(0.4, 0, 1, 1)` 出场 |
| **200-400ms** | 微交互时长 | 按钮反馈 150ms, 卡片悬停 300ms, 页面切换 300ms, 弹窗 200ms |
| **不打断** | 动画不应阻塞用户操作 | 使用 `pointer-events` 控制交互时序 |
| **减弱动效** | 尊重用户系统设置 | `@media (prefers-reduced-motion: reduce)` |

### 5.2 缓动曲线

```css
:root {
  /* 标准曲线 — 大多数过渡 */
  --ease-standard: cubic-bezier(0.4, 0, 0.2, 1);

  /* 入场 — 元素出现 */
  --ease-enter: cubic-bezier(0, 0, 0.2, 1);

  /* 出场 — 元素消失 */
  --ease-exit: cubic-bezier(0.4, 0, 1, 1);

  /* 弹性 — 特殊强调 (谨慎使用) */
  --ease-bounce: cubic-bezier(0.34, 1.56, 0.64, 1);
}
```

### 5.3 时长梯度

```css
:root {
  --duration-instant: 100ms;   /* 按钮按下反馈 */
  --duration-fast: 150ms;      /* hover 颜色变化 */
  --duration-normal: 200ms;    /* 弹窗进出 */
  --duration-slow: 300ms;      /* 卡片浮起、页面过渡 */
  --duration-glacial: 500ms;   /* 大面积场景过渡 */
  --duration-brand: 800ms;     /* 品牌主页大动画 */
}
```

### 5.4 关键动效清单

#### A. 页面入场

```css
/* 区块滚动渐显 */
.scroll-reveal {
  opacity: 0;
  transform: translateY(24px);
  transition: opacity 500ms var(--ease-enter),
              transform 500ms var(--ease-enter);
}

.scroll-reveal.visible {
  opacity: 1;
  transform: translateY(0);
}
```

```
触发时机: IntersectionObserver threshold 0.15
延迟: 每个后续区块 +80ms (stagger)
仅触发一次 (once: true)
```

#### B. 卡片悬浮

```css
.card-hover {
  transition: transform 300ms var(--ease-standard),
              box-shadow 300ms var(--ease-standard);
}

.card-hover:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-card-hover);
}
```

#### C. 按钮反馈

```css
.button-press {
  transition: transform 100ms var(--ease-standard),
              background-color 150ms var(--ease-standard);
}

.button-press:active {
  transform: scale(0.97);
}
```

#### D. 购物车图标弹出

```css
/* 加购时购物车图标弹性动画 */
.cart-bounce {
  animation: cartPop 400ms var(--ease-bounce);
}

@keyframes cartPop {
  0%   { transform: scale(1); }
  30%  { transform: scale(1.25); }
  60%  { transform: scale(0.9); }
  100% { transform: scale(1); }
}
```

#### E. 骨架屏

```
使用 shimmer 动画（非 pulse）:
  background: linear-gradient(
    90deg,
    var(--color-warm-gray) 0%,
    var(--color-cool-gray) 40%,
    var(--color-warm-gray) 80%
  );
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;

@keyframes shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
```

#### F. 弹窗/抽屉

```
入场:
  overlay: opacity 0→1, 200ms
  panel: translateX(100%)→0 (抽屉) / scale(0.95)+opacity(0)→(1,1) (弹窗)
  200ms var(--ease-enter)

出场:
  overlay: opacity 1→0, 150ms
  panel: 反向
  150ms var(--ease-exit)
```

#### G. 品牌主页 — 昼夜循环

```
使用 Three.js / Canvas:
- 灯光角度旋转: 0°→180° (对应日出→日落)
- 环境光色温: 暖橙→白→冷蓝
- 天空球颜色: 天蓝→橙红→深蓝紫
- 植物: 轻微摇曳 (sin 波)

性能:
- requestAnimationFrame
- 仅更新 uniforms (不重建几何体)
- 背景场景 30fps 即可 (非 60fps)
- ResizeObserver 绑定 Canvas 尺寸
```

#### H. 品牌主页 — 视差光照

```
鼠标在 Hero 区域移动时:
- 光照位置轻微跟随鼠标 (clamp 在 -20px ~ 20px)
- 使用 CSS custom properties 驱动:
  --mouse-x: 0.5 (0~1)
  --mouse-y: 0.5 (0~1)
- transform: translate(calc((var(--mouse-x) - 0.5) * 40px),
                        calc((var(--mouse-y) - 0.5) * 40px));
- 使用 requestAnimationFrame + passive event listener
```

#### I. 数字滚动 (价格/倒计时)

```
秒杀倒计时:
  font-mono, tabular-nums (防止宽度跳动)
  数字变化: 轻微 scale(1.1)→(1.0), 200ms

价格变化 (切换 SKU):
  font-mono, tabular-nums
  旧价格 fadeOut 150ms + 新价格 fadeIn 150ms
  使用 AnimatePresence (Framer Motion) 或 CSS transition
```

### 5.5 动效开关

```css
/* 尊重系统动效偏好 */
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
```

---

## 六、品牌主页暗色模式 vs 商城亮色模式

### 6.1 场景分离策略

品牌主页和商城使用**不同的 CSS 变量范围**，而非全局主题切换：

```css
/* 品牌主页 — 强制暗色 */
[data-theme-scope="brand"] {
  --bg-primary: #1A1A1A;
  --bg-secondary: #252525;
  --text-primary: #FAFAF8;
  --text-secondary: rgba(250, 250, 248, 0.70);
  --accent: #C8A96E;
  --glass-bg: rgba(26, 26, 26, 0.70);
  --glass-border: rgba(255, 255, 255, 0.10);
}

/* 商城 — 默认亮色 (尊重系统偏好) */
[data-theme-scope="marketplace"] {
  --bg-primary: #FAFAF8;
  --bg-secondary: #F5F3F0;
  --text-primary: #1A1A1A;
  --text-secondary: #6B6B6B;
  --accent: #2D8B6E;
  --glass-bg: rgba(255, 255, 255, 0.70);
  --glass-border: rgba(255, 255, 255, 0.30);
}

/* 商城 — 系统暗色模式覆盖 */
@media (prefers-color-scheme: dark) {
  [data-theme-scope="marketplace"] {
    --bg-primary: #1A1A1A;
    --bg-secondary: #252525;
    --text-primary: #FAFAF8;
    --glass-bg: rgba(26, 26, 26, 0.70);
  }
}
```

### 6.2 过渡

从品牌主页进入商城时，背景从暗色平滑过渡到亮色（或反之）：

```css
.page-transition {
  transition: background-color 500ms var(--ease-standard);
}
```

---

## 七、图标系统

### 7.1 图标库

使用 **Lucide** (轻量 SVG) 作为主图标库。线性图标为主，特定场景使用填充图标。

### 7.2 图标尺寸

| Size | px | 用途 |
|------|-----|------|
| `xs` | 14px | 行内图标 (标签、评分星) |
| `sm` | 16px | 按钮内图标、列表 |
| `md` | 20px | 导航图标 |
| `lg` | 24px | 功能入口、分类图标 |
| `xl` | 32px | 空状态插画辅助 |

### 7.3 自定义品牌图标

```tsx
// 品牌 Logo — 抽象化的冰晶 + 叶片
// 设计原则：简洁几何、线条流畅、可缩放至 16px
```

---

## 八、间距与布局系统

### 8.1 页面容器

```css
.container-page {
  max-width: 1280px;   /* 商城最大宽度 */
  margin: 0 auto;
  padding: 0 24px;     /* desktop */
  padding: 0 16px;     /* mobile */
}
```

### 8.2 区块间距

| Token | Desktop | Mobile | 用途 |
|-------|---------|--------|------|
| `section-xl` | 120px | 64px | 页面级大区块 |
| `section-lg` | 80px | 48px | 标准区块分隔 |
| `section-md` | 48px | 32px | 相关区块组内分隔 |
| `section-sm` | 24px | 16px | 紧密区块分隔 |

---

## 九、图片规范

### 9.1 图片尺寸标准

| 场景 | 宽高比 | 推荐尺寸 | 格式 |
|------|--------|---------|------|
| 商品主图 | 1:1 或 4:5 | 800×800 / 800×1000 | WebP |
| 商品详情图 | 自适应 | 宽度 1200px | WebP |
| Banner | 16:5 | 1920×600 | WebP |
| 分类图标 | 1:1 | 120×120 | SVG/WebP |
| 头像 | 1:1 | 200×200 | WebP |
| 店铺 Logo | 1:1 | 200×200 | WebP |

### 9.2 Next.js Image 配置

```tsx
// next.config.ts
const nextConfig = {
  images: {
    formats: ['image/webp', 'image/avif'],
    remotePatterns: [
      {
        protocol: 'https',
        hostname: 'cdn.icedmall.com',
      },
    ],
    deviceSizes: [640, 768, 1024, 1280, 1536],
    imageSizes: [16, 32, 48, 64, 96, 128, 256, 384],
  },
};
```

### 9.3 渐进式加载

```tsx
// 使用 blurDataURL 实现 LQIP (Low Quality Image Placeholder)
<Image
  src={product.mainImage}
  alt={product.name}
  fill
  className="object-cover"
  placeholder="blur"
  blurDataURL={product.blurHash}  // 后端预生成的 8px blurhash
  sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 25vw"
/>
```

---

## 十、管理后台设计 (Ant Design 定制)

### 10.1 Ant Design 主题 Token 覆盖

```typescript
// admin/theme.ts — 仅用于商家后台 + 平台管理后台
import type { ThemeConfig } from 'antd';

export const adminTheme: ThemeConfig = {
  token: {
    colorPrimary: '#2D8B6E',
    colorSuccess: '#2D8B6E',
    colorWarning: '#D4956A',
    colorError: '#C45A5A',
    colorInfo: '#6B8AAB',
    borderRadius: 6,
    fontFamily: "'Inter', 'Noto Sans SC', sans-serif",
  },
  components: {
    Layout: {
      headerBg: 'rgba(255,255,255,0.70)',
      headerHeight: 56,
      siderBg: '#FAFAF8',
    },
    Table: {
      headerBg: '#F5F3F0',
    },
  },
};
```

### 10.2 后台不适用品牌玻璃风格

管理后台**不使用**毛玻璃效果——保持高效、专业的企业工具风格。Ant Design 原生组件即可。

---

## 十一、设计检查清单

在进入代码实现前，确认以下设计决策已达成：

- [ ] 色彩 token 命名 + 数值已定稿
- [ ] 字体栈已确认 (Inter + Noto Sans SC + Playfair Display)
- [ ] 玻璃材质 CSS 变量已定义
- [ ] 5 个核心组件 (GlassCard, ProductCard, ProductGrid, GlassButton, SearchBar) 设计稿已定
- [ ] 品牌主页暗色 vs 商城亮色分离方案已确认
- [ ] 动效曲线 + 时长梯度已确认
- [ ] 图片尺寸标准 + 渐进加载方案已确认
- [ ] 响应式断点 + 移动端适配策略已确认
- [ ] 管理后台 Ant Design 主题 token 已确认

---

> **下一文档**: [18-Frontend-Implementation](./18-Frontend-Implementation.md) — 开发任务分解、阶段规划、工程规范
