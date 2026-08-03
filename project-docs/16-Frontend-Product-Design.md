# 16 — 前端产品设计文档

> 最后更新: 2026-08-02 | 版本: V1.0
>
> 依赖: [05-API-Specification](./05-API-Specification.md) · [15-Front-End-Technology-Selection](./15-Front-End-Technology-Selection.md) · [prompt.md](./prompt.md)
>
> 下一文档: [17-Frontend-Design-System](./17-Frontend-Design-System.md)

---

## 一、产品定位与设计哲学

### 1.1 品牌世界观

> **东方自然主义 × 未来玻璃艺术**
>
> 不是普通商城，而是一个拥有艺术世界观的高端生活方式平台。

| 维度 | 定义 |
|------|------|
| **品牌人格** | 静谧的东方匠人 × 未来主义建筑师 |
| **空间感** | Apple Vision Pro 的景深感 + 日本庭院的留白美学 |
| **视觉语言** | Glassmorphism 毛玻璃、柔光弥散、透明层次、渐变阴影 |
| **情感目标** | 用户进入主页先感受品牌世界，再进入商城完成交易 |
| **差异化** | 首页 = 品牌体验（Brand Experience），商城 = 转化效率（Conversion Efficiency） |

### 1.2 设计原则

| 编号 | 原则 | 说明 | 检验标准 |
|------|------|------|---------|
| DP1 | **克制** | 每个像素都有存在的理由。不堆砌装饰 | 删除任意元素后体验变差 |
| DP2 | **自然** | 动画如自然呼吸，不生硬不炫技 | 60fps，仅用 transform + opacity |
| DP3 | **层次** | 景深通过毛玻璃 + 半透明 + 模糊建立，非扁平 | 前/中/后景清晰可辨 |
| DP4 | **转化** | 商城部分以转化率为北极星指标 | 加购流程 ≤ 3 步 |
| DP5 | **性能优先** | 任何视觉效果不能牺牲加载速度 | Lighthouse ≥ 90 |

### 1.3 用户心智模型

```
用户进入 iceMall.com
         │
         ▼
    ┌─────────┐
    │  品牌主页  │  ← 沉浸式品牌体验，建立信任与情感连接
    │  (Home)  │    用户在此停留 10-30 秒感受品牌
    └────┬────┘
         │  "探索商城" / 顶部导航 / 滚动到底部入口
         ▼
    ┌─────────┐
    │   商城    │  ← 高效浏览与交易，转化漏斗起点
    │(Marketplace)│  用户在此完成：搜索→浏览→加购→下单
    └────┬────┘
         │
    ┌────┴────┬────────────┬────────────┐
    ▼         ▼            ▼            ▼
  商品详情   购物车        结算          支付
  (决策)    (收集)       (确认)        (成交)
```

---

## 二、信息架构 (IA)

### 2.1 整体站点地图

```
iceMall.com
│
├── /                          # 品牌主页 (Home — Brand Experience)
│   ├── Hero (全屏品牌空间)
│   ├── 品牌理念
│   ├── 精选作品
│   └── 进入商城入口
│
├── /marketplace               # 商城首页 (Marketplace Landing)
│   ├── 顶部导航
│   ├── Hero Banner
│   ├── 分类入口
│   ├── 精选推荐
│   ├── 新品上市
│   ├── 活动专区
│   └── 底部导航
│
├── /search                    # 搜索页
│   ├── 搜索框 + 热门搜索
│   ├── 搜索结果列表
│   └── 筛选面板
│
├── /categories/[slug]         # 分类商品列表
│
├── /products/[id]             # 商品详情页 (PDP)
│
├── /shop/[sellerId]           # 商家店铺页
│
├── /cart                      # 购物车 (需登录)
│
├── /checkout                  # 确认订单/结算 (需登录)
│
├── /orders                    # 我的订单 (需登录)
│   └── /orders/[orderNo]      # 订单详情
│
├── /pay/[orderNo]             # 支付页 (需登录)
│
├── /user                      # 个人中心 (需登录)
│   ├── /user/profile          # 个人资料
│   ├── /user/addresses        # 地址管理
│   ├── /user/favorites        # 我的收藏
│   ├── /user/history          # 浏览历史
│   ├── /user/coupons          # 优惠券
│   ├── /user/points           # 积分
│   ├── /user/balance          # 余额
│   ├── /user/after-sales      # 售后
│   ├── /user/notifications    # 消息中心
│   └── /user/security         # 账号安全
│
├── /sign                       # 签到 (需登录)
│
├── /flash                      # 秒杀频道
│
├── /coupons                    # 领券中心
│
├── /ai                         # AI 购物助手 (需登录)
│
├── /chat                       # 消息/客服 (需登录)
│
├── /auth
│   ├── /auth/login             # 登录
│   ├── /auth/register          # 注册
│   └── /auth/reset-password    # 找回密码
│
├── /seller                     # 商家后台 (需登录 + SELLER)
│   ├── /seller/dashboard       # 仪表盘
│   ├── /seller/products        # 商品管理
│   ├── /seller/orders          # 订单管理
│   ├── /seller/shop            # 店铺设置
│   ├── /seller/coupons         # 优惠券管理
│   ├── /seller/templates       # 回复模板
│   ├── /seller/knowledge       # 知识库
│   ├── /seller/finance         # 结算管理
│   └── /seller/apply           # 入驻申请
│
└── /admin                      # 平台管理后台 (需登录 + ADMIN)
    ├── /admin/dashboard        # 数据仪表盘
    ├── /admin/users            # 用户管理
    ├── /admin/sellers          # 商家审核
    ├── /admin/products         # 商品管理
    ├── /admin/orders           # 订单管理
    ├── /admin/categories       # 分类管理
    ├── /admin/home             # 首页装修
    ├── /admin/coupons          # 优惠券管理
    ├── /admin/flash            # 秒杀管理
    ├── /admin/after-sales      # 售后管理
    ├── /admin/finance          # 财务管理
    ├── /admin/roles            # 角色权限
    └── /admin/logs             # 操作日志
```

### 2.2 页面分组与 Layout

```
Next.js App Router Route Groups:

app/
├── (brand)/               # 品牌体验组 — 自定义 Layout (无传统导航)
│   ├── layout.tsx         # 品牌 Layout: 全屏沉浸，毛玻璃导航
│   └── page.tsx           # 品牌主页
│
├── (marketplace)/         # 商城组 — 商城 Layout (顶部导航+底部)
│   ├── layout.tsx         # 商城 Layout: Header + Main + Footer
│   ├── page.tsx           # 商城首页 (/marketplace)
│   ├── search/
│   ├── categories/
│   ├── products/
│   ├── shop/
│   ├── flash/
│   └── coupons/
│
├── (shop)/                # 交易组 — 需登录 + 简化导航
│   ├── layout.tsx         # 交易 Layout: 返回按钮 + 步骤指示器
│   ├── cart/
│   ├── checkout/
│   ├── orders/
│   └── pay/
│
├── (user)/                # 用户中心组 — 需登录 + 侧边栏
│   ├── layout.tsx         # 用户 Layout: 侧边导航 + 内容区
│   ├── profile/
│   ├── addresses/
│   ├── favorites/
│   ├── history/
│   ├── coupons/
│   ├── points/
│   ├── balance/
│   ├── after-sales/
│   ├── notifications/
│   └── security/
│
├── auth/                  # 认证组 — 无导航，居中卡片
│   ├── layout.tsx         # Auth Layout: 居中卡片 + 毛玻璃背景
│   ├── login/
│   ├── register/
│   └── reset-password/
│
├── seller/                # 商家后台 — Ant Design ProLayout
│   └── (all seller routes)
│
└── admin/                 # 管理后台 — Ant Design ProLayout
    └── (all admin routes)
```

---

## 三、页面路由与渲染策略

### 3.1 路由表

| 路由 | 页面 | 渲染策略 | 认证 | SEO | 说明 |
|------|------|---------|------|-----|------|
| `/` | 品牌主页 | SSG | 🌐 | ✅ | 品牌体验入口，静态生成 |
| `/marketplace` | 商城首页 | ISR 60s | 🌐 | ✅ | 商品数据有变化，60s 增量再生 |
| `/search` | 搜索页 | SSR + Client | 🌐 | ❌ | 搜索结果是动态的 |
| `/categories/[slug]` | 分类列表 | ISR 60s | 🌐 | ✅ | 分类下商品有一定时效 |
| `/products/[id]` | 商品详情 | ISR 300s + Client islands | 🌐 | ✅ | 基础数据缓存，库存/价格 client fetch |
| `/shop/[sellerId]` | 商家店铺 | ISR 300s | 🌐 | ✅ | 店铺信息变化不频繁 |
| `/cart` | 购物车 | Client Component | 🔒 | ❌ | 纯交互，无需 SEO |
| `/checkout` | 结算 | Client Component | 🔒 | ❌ | 表单密集 |
| `/orders` | 订单列表 | SSR | 🔒 | ❌ | 需实时订单状态 |
| `/orders/[orderNo]` | 订单详情 | SSR | 🔒 | ❌ | 含物流实时信息 |
| `/pay/[orderNo]` | 支付 | Client Component | 🔒 | ❌ | 二维码 + 轮询 |
| `/user/*` | 个人中心 | Client Component | 🔒 | ❌ | 私人数据 |
| `/sign` | 签到 | Client Component | 🔒 | ❌ | 签到交互 |
| `/flash` | 秒杀 | ISR 10s + Client | 🌐 | ✅ | 秒杀状态高频变化 |
| `/coupons` | 领券中心 | ISR 60s | 🌐 | ✅ | 券模板变化不频繁 |
| `/ai` | AI 助手 | Client Component | 🔒 | ❌ | 对话式交互 |
| `/chat` | 消息 | Client Component | 🔒 | ❌ | WebSocket 实时通讯 |
| `/auth/*` | 认证 | Client Component | 🌐 | ❌ | 表单交互 |
| `/seller/*` | 商家后台 | SPA (Client only) | 🏪 | ❌ | 管理功能 |
| `/admin/*` | 管理后台 | SPA (Client only) | 🔑 | ❌ | 管理功能 |

### 3.2 ISR 配置策略

```typescript
// 商品列表 — 高频更新
export const revalidate = 60;

// 商品详情 — 中频更新
export const revalidate = 300;

// 秒杀页面 — 极高频更新
export const revalidate = 10;

// 静态页面 — 按需 revalidate
export const revalidate = 3600;

// 动态路由 generateStaticParams
export async function generateStaticParams() {
  // 预生成 Top 100 热门商品详情页
  const hotProducts = await getHotProducts(100);
  return hotProducts.map(p => ({ id: p.productId }));
}
```

---

## 四、核心用户流程

### 4.1 主转化路径 (Golden Path)

```
                品牌主页 (/)
                     │
                     │ 滚动浏览品牌内容 (~20s)
                     │ 点击 "探索商城" 或顶部导航 "商城"
                     ▼
              商城首页 (/marketplace)
                     │
          ┌──────────┼──────────┐
          │          │          │
    搜索商品    浏览分类     点击推荐
          │          │          │
          └──────────┼──────────┘
                     ▼
              商品列表 (/search 或 /categories/[slug])
                     │
                     │ 浏览 + 筛选 + 排序
                     │ 点击商品卡片
                     ▼
            商品详情 (/products/[id])
                     │
          ┌──────────┼──────────┐
          │                     │
     "立即购买"           "加入购物车"
          │                     │
          ▼                     ▼
    确认订单 (/checkout)    继续浏览 / 去购物车
          │                     │
          │                     ▼
          │              购物车 (/cart)
          │                     │
          │              选择商品 → "去结算"
          │                     │
          └──────────┬──────────┘
                     ▼
              确认订单 (/checkout)
                     │
                ┌────┴────┐
                │         │
            选择地址   选择优惠券
                │         │
                └────┬────┘
                     ▼
              "提交订单"
                     │
                     ▼
              支付页 (/pay/[orderNo])
                     │
                ┌────┴────┬──────────┐
                │         │          │
             微信支付   支付宝     余额支付
                │         │          │
                └────┬────┘          │
                     ▼               │
               支付成功 ←─────────────┘
                     │
                     ▼
            订单详情 (/orders/[orderNo])
                     │
                "确认收货" (到货后)
                     │
                     ▼
                "去评价"
```

### 4.2 辅助流程

#### 4.2.1 售后退款流程

```
订单详情 → "申请售后" → 填写原因/上传凭证 → 提交
    → 商家审核 → (通过) → 退货填写物流 → 商家收货确认 → 退款到账
              → (拒绝) → 查看拒绝原因 → 可修改重提
```

#### 4.2.2 领券用券流程

```
领券中心 → 浏览券模板 → "立即领取"
    → 我的优惠券 (可用)
    → 下单时自动匹配可用券 → 选择最优组合 → 提交订单自动抵扣
```

#### 4.2.3 商家发货流程

```
商家后台 → 待发货订单 → 点击"发货"
    → 填写物流单号 + 选择物流公司 → 确认发货
    → 订单状态 → "待收货"，通知买家
```

### 4.3 漏斗分析埋点

| 步骤 | 事件名 | 页面 | 关键参数 |
|------|--------|------|---------|
| 1. 访问 | `page_view` | 品牌主页 | referrer, device |
| 2. 进入商城 | `enter_marketplace` | 商城首页 | entry_source (hero/cta/nav) |
| 3. 浏览商品 | `product_impression` | 列表/搜索 | productId, position, search_query |
| 4. 查看详情 | `product_view` | 商品详情 | productId, from (search/category/recommend) |
| 5. 加购 | `add_to_cart` | 商品详情/列表 | productId, skuId, quantity, price |
| 6. 进入结算 | `begin_checkout` | 结算 | cartItems count, totalAmount |
| 7. 提交订单 | `place_order` | 结算 | orderNo, totalAmount, discountAmount, couponIds |
| 8. 发起支付 | `initiate_payment` | 支付 | orderNo, channel, amount |
| 9. 支付成功 | `payment_success` | 支付结果 | orderNo, channel, amount |
| 10. 确认收货 | `confirm_receipt` | 订单详情 | orderNo |

---

## 五、关键页面结构

### 5.1 品牌主页 (`/`) — Brand Experience

```
┌─────────────────────────────────────────────────────┐
│ 顶部导航 (透明毛玻璃, position: fixed)                │
│ [Logo]              [商城] [关于] [语言]              │
├─────────────────────────────────────────────────────┤
│                                                     │
│  Hero Section (100vh)                                │
│  ┌───────────────────────────────────────────────┐  │
│  │                                               │  │
│  │    背景: 动态东方庭院 (时间流逝动画)             │  │
│  │         玻璃建筑 + 植物 + 柔光                   │  │
│  │                                               │  │
│  │    中央:                                       │  │
│  │        冰美商城                                 │  │
│  │        "科技，自然融入生活"                       │  │
│  │                                               │  │
│  │        [探索商城]  (Glass 按钮)                 │  │
│  │                                               │  │
│  │    鼠标移动: 光影轻微跟随 (parallax)             │  │
│  │    滚动: 白天→黄昏→夜晚                         │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
├─────────────────────────────────────────────────────┤
│                                                     │
│  Section 1: 品牌理念                                │
│  ┌───────────────────────────────────────────────┐  │
│  │  左: 大段留白 + 手写体理念文字                    │  │
│  │  "我们相信，科技应该像一杯好咖啡——"               │  │
│  │  "自然、克制、但又充满温度"                       │  │
│  │  右: 抽象的植物/玻璃装置艺术图                     │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
├─────────────────────────────────────────────────────┤
│                                                     │
│  Section 2: 精选作品 (横向滚动)                      │
│  ┌───────────────────────────────────────────────┐  │
│  │  Title: "精选作品"                              │  │
│  │                                               │  │
│  │  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐         │  │
│  │  │ 作品A │ │ 作品B │ │ 作品C │ │ 作品D │  ← 滑动  │  │
│  │  │ 图片 │ │ 图片 │ │ 图片 │ │ 图片 │         │  │
│  │  │ 名称 │ │ 名称 │ │ 名称 │ │ 名称 │         │  │
│  │  │ 介绍 │ │ 介绍 │ │ 介绍 │ │ 介绍 │         │  │
│  │  └──────┘ └──────┘ └──────┘ └──────┘         │  │
│  │                                               │  │
│  │  悬停: scale(1.02) + 柔光阴影增强               │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
├─────────────────────────────────────────────────────┤
│                                                     │
│  Section 3: 创作者空间                              │
│  ┌───────────────────────────────────────────────┐  │
│  │  理念 · 经历 · 审美 · 方向                       │  │
│  │  毛玻璃卡片 + 文字 + 留白                        │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
├─────────────────────────────────────────────────────┤
│                                                     │
│  Section 4: 进入商城 (巨大入口)                      │
│  ┌───────────────────────────────────────────────┐  │
│  │                                               │  │
│  │         [ Enter Marketplace ]                  │  │
│  │         背景: 从自然空间过渡到商城空间             │  │
│  │         毛玻璃巨型按钮 + 柔光动画                 │  │
│  │                                               │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
├─────────────────────────────────────────────────────┤
│ Footer: 品牌信息 · 链接 · 社交媒体 · 版权             │
└─────────────────────────────────────────────────────┘
```

### 5.2 商城首页 (`/marketplace`) — Conversion Landing

```
┌─────────────────────────────────────────────────────┐
│ 顶部导航 (毛玻璃, sticky)                              │
│ [Logo] [搜索框...] [分类▾] [购物车🛒] [用户👤]         │
├─────────────────────────────────────────────────────┤
│                                                     │
│  Hero Banner (40vh)                                 │
│  ┌───────────────────────────────────────────────┐  │
│  │  轮播 Banner: 3-5 张精选活动                    │  │
│  │  毛玻璃半透明叠加层 + 标题 + CTA                 │  │
│  │  自动轮播 5s + 手动滑动                         │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  分类入口 (水平滚动)                                  │
│  ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐             │
│  │📱 │ │💻 │ │👗 │ │👟 │ │🏠 │ │✨ │  ← 滑动       │
│  │数码│ │电脑│ │服饰│ │鞋靴│ │家居│ │美妆│             │
│  └───┘ └───┘ └───┘ └───┘ └───┘ └───┘             │
│                                                     │
│  精选推荐 (横向滚动)                                  │
│  ┌──────────────────────────────────────────────┐  │
│  │ 标题: "为你精选"                  [查看全部 →]  │  │
│  │ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐         │  │
│  │ │ 商品 │ │ 商品 │ │ 商品 │ │ 商品 │  ← 滑动   │  │
│  │ └──────┘ └──────┘ └──────┘ └──────┘         │  │
│  └──────────────────────────────────────────────┘  │
│                                                     │
│  新品上市 (网格)                                     │
│  ┌──────────────────────────────────────────────┐  │
│  │ 标题: "新品首发"                              │  │
│  │ ┌────┐ ┌────┐ ┌────┐ ┌────┐                 │  │
│  │ │    │ │    │ │    │ │    │                 │  │
│  │ └────┘ └────┘ └────┘ └────┘                 │  │
│  │ ┌────┐ ┌────┐ ┌────┐ ┌────┐                 │  │
│  │ │    │ │    │ │    │ │    │                 │  │
│  │ └────┘ └────┘ └────┘ └────┘                 │  │
│  └──────────────────────────────────────────────┘  │
│                                                     │
│  活动专区                                           │
│  ┌──────────────────────────────────────────────┐  │
│  │ ┌─────────────┐  ┌─────────────┐             │  │
│  │ │  秒杀频道    │  │  领券中心    │             │  │
│  │ │  Flash Sale │  │  Coupons    │             │  │
│  │ └─────────────┘  └─────────────┘             │  │
│  └──────────────────────────────────────────────┘  │
│                                                     │
├─────────────────────────────────────────────────────┤
│ Footer                                              │
└─────────────────────────────────────────────────────┘
```

### 5.3 商品详情页 (`/products/[id]`) — PDP

```
┌─────────────────────────────────────────────────────┐
│ 顶部导航                                            │
├─────────────────────────────────────────────────────┤
│  面包屑: 首页 > 分类 > 商品名                         │
│                                                     │
│  ┌──────────────────────┐  ┌────────────────────┐  │
│  │                      │  │  商品名称            │  │
│  │                      │  │  "手工陶瓷茶杯"       │  │
│  │    商品主图轮播       │  │                     │  │
│  │    (多图 + 放大镜)    │  │  ¥128.00  ¥158.00   │  │
│  │                      │  │  到手价    原价       │  │
│  │                      │  │                     │  │
│  │                      │  │  [热销] [新品]       │  │
│  │                      │  │                     │  │
│  │                      │  │  规格选择:            │  │
│  │                      │  │  颜色: [天青][月白]   │  │
│  │                      │  │  尺寸: [标准][加大]   │  │
│  │                      │  │                     │  │
│  │                      │  │  数量: [-] 1 [+]     │  │
│  │                      │  │                     │  │
│  │                      │  │  [加入购物车] [立即购买]│  │
│  │                      │  │                     │  │
│  │                      │  │  服务: 7天退换 正品保证│  │
│  └──────────────────────┘  └────────────────────┘  │
│                                                     │
│  ┌──────────────────────────────────────────────┐  │
│  │  店铺卡片                                      │  │
│  │  [Logo] 冰美精选陶瓷  ★4.8  3650关注           │  │
│  │                          [关注店铺] [进店逛逛]  │  │
│  └──────────────────────────────────────────────┘  │
│                                                     │
│  ┌──────────────────────────────────────────────┐  │
│  │  Tab: 商品详情 | 规格参数 | 评价(86)            │  │
│  │  ───────────────────────────────────────       │  │
│  │  图文详情内容...                                │  │
│  └──────────────────────────────────────────────┘  │
│                                                     │
│  ┌──────────────────────────────────────────────┐  │
│  │  评价摘要                                      │  │
│  │  ★4.7  86条评价                               │  │
│  │  5星 ████████████ 60                           │  │
│  │  4星 ████ 15                                   │  │
│  │  ...                                          │  │
│  │                                               │  │
│  │  评价列表 (筛选: 有图/好评/最新)                 │  │
│  └──────────────────────────────────────────────┘  │
│                                                     │
│  推荐商品 (横向滚动: "看了又看")                      │
└─────────────────────────────────────────────────────┘
```

### 5.4 购物车 (`/cart`)

```
┌─────────────────────────────────────────────────────┐
│ 顶部导航 (简化: ← 返回 + "购物车")                    │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌─ 按店铺分组 ──────────────────────────────────┐  │
│  │ [ ] 冰美精选陶瓷                    [领券]    │  │
│  │ ┌──────────────────────────────────────────┐ │  │
│  │ │ [ ] [图] 手工陶瓷茶杯 天青/标准           │ │  │
│  │ │         ¥128.00          [-] 2 [+]       │ │  │
│  │ │         小计: ¥256.00              [删除] │ │  │
│  │ └──────────────────────────────────────────┘ │  │
│  │ ┌──────────────────────────────────────────┐ │  │
│  │ │ [ ] [图] 竹节茶则 标准                    │ │  │
│  │ │         ¥68.00           [-] 1 [+]       │ │  │
│  │ │         小计: ¥68.00               [删除] │ │  │
│  │ └──────────────────────────────────────────┘ │  │
│  └──────────────────────────────────────────────┘  │
│                                                     │
│  ┌─ 按店铺分组 ──────────────────────────────────┐  │
│  │ [ ] 云栖茶具                                  │  │
│  │ ┌──────────────────────────────────────────┐ │  │
│  │ │ [ ] [图] 紫砂壶 标准                      │ │  │
│  │ │         ¥388.00          [-] 1 [+]       │ │  │
│  │ └──────────────────────────────────────────┘ │  │
│  └──────────────────────────────────────────────┘  │
│                                                     │
├─────────────────────────────────────────────────────┤
│ 底部结算栏 (sticky)                                  │
│  [ ] 全选    合计: ¥712.00    节省: ¥xx             │
│                              [去结算 (3)]           │
└─────────────────────────────────────────────────────┘
```

### 5.5 结算页 (`/checkout`)

```
┌─────────────────────────────────────────────────────┐
│ 步骤指示器: ① 确认订单 → ② 支付 → ③ 完成            │
├─────────────────────────────────────────────────────┤
│                                                     │
│  收货地址                                           │
│  ┌──────────────────────────────────────────────┐  │
│  │ ○ 张三  138****8000  北京市朝阳区望京SOHO      │  │
│  │    [默认] [编辑]                              │  │
│  │ ○ 李四  139****9000  上海市浦东新区...         │  │
│  │ [+ 新增地址]                                  │  │
│  └──────────────────────────────────────────────┘  │
│                                                     │
│  商品清单                                           │
│  ┌──────────────────────────────────────────────┐  │
│  │ [图] 手工陶瓷茶杯 ×2    ¥128.00    ¥256.00    │  │
│  │ [图] 竹节茶则 ×1        ¥68.00     ¥68.00    │  │
│  └──────────────────────────────────────────────┘  │
│                                                     │
│  优惠券                                             │
│  ┌──────────────────────────────────────────────┐  │
│  │ 可用优惠券: 3张                               │  │
│  │ ☑ 平台满100减10 (-¥10.00)                    │  │
│  │ ☑ 店铺满50减10  (-¥10.00)                    │  │
│  │ ☐ 满200减30    (未达门槛)                     │  │
│  └──────────────────────────────────────────────┘  │
│                                                     │
│  备注: [________________] 选填                       │
│                                                     │
├─────────────────────────────────────────────────────┤
│                                                     │
│  金额汇总:                                          │
│  商品总额: ¥324.00                                  │
│  优惠券:   -¥20.00                                  │
│  运费:     免运费                                   │
│  ─────────────────────                              │
│  实付金额: ¥304.00                                  │
│                                                     │
│  [提交订单]                                         │
└─────────────────────────────────────────────────────┘
```

---

## 六、组件树

### 6.1 共享组件层次

```
packages/ui/components/

├── layout/
│   ├── BrandLayout.tsx           # 品牌主页布局
│   ├── MarketplaceLayout.tsx     # 商城布局
│   ├── ShopLayout.tsx            # 交易布局
│   ├── UserLayout.tsx            # 用户中心布局
│   ├── AuthLayout.tsx            # 认证布局
│   ├── Header.tsx                # 商城顶部导航
│   ├── Footer.tsx                # 商城底部
│   └── MobileNav.tsx             # 移动端底部导航
│
├── brand/                        # 品牌主页专用组件
│   ├── HeroSection.tsx           # Hero 全屏空间
│   ├── ParallaxBackground.tsx    # 视差滚动背景
│   ├── DayNightCycle.tsx         # 昼夜循环动画
│   ├── BrandPhilosophy.tsx       # 品牌理念区域
│   ├── FeaturedWorks.tsx         # 精选作品横向滚动
│   ├── CreatorSpace.tsx          # 创作者空间
│   └── MarketplaceEntry.tsx      # 进入商城大门
│
├── product/                      # 商品相关组件
│   ├── ProductCard.tsx           # 商品卡片 (通用)
│   ├── ProductCardSkeleton.tsx   # 卡片骨架屏
│   ├── ProductGrid.tsx           # 商品网格
│   ├── ProductHorizontalScroll.tsx  # 横向滚动商品列表
│   ├── ProductImageGallery.tsx   # 商品图片轮播 + 放大
│   ├── SkuSelector.tsx           # SKU 规格选择器
│   ├── PriceDisplay.tsx          # 价格展示 (到手价/原价/折扣)
│   ├── QuantityStepper.tsx       # 数量加减
│   ├── HotBadge.tsx              # 热销标签
│   └── SalesTags.tsx             # 销售标签组
│
├── cart/                         # 购物车组件
│   ├── CartDrawer.tsx            # 侧边购物车抽屉
│   ├── CartItem.tsx              # 购物车单品
│   ├── CartGroup.tsx             # 按店铺分组的购物车区块
│   ├── CartSummary.tsx           # 购物车底部汇总栏
│   └── EmptyCart.tsx             # 空购物车状态
│
├── order/                        # 订单组件
│   ├── OrderCard.tsx             # 订单卡片
│   ├── OrderStatusBadge.tsx      # 订单状态标签
│   ├── OrderTimeline.tsx         # 订单物流时间线
│   ├── OrderItemList.tsx         # 订单商品清单
│   └── AfterSaleForm.tsx         # 售后申请表单
│
├── checkout/                     # 结算组件
│   ├── AddressSelector.tsx       # 地址选择器
│   ├── AddressForm.tsx           # 地址编辑表单
│   ├── CouponSelector.tsx        # 优惠券选择器
│   ├── OrderSummary.tsx          # 订单金额汇总
│   └── PaymentMethodSelector.tsx # 支付方式选择
│
├── user/                         # 用户中心组件
│   ├── UserSidebar.tsx           # 用户中心侧边导航
│   ├── ProfileForm.tsx           # 个人资料表单
│   ├── AvatarUpload.tsx          # 头像上传
│   ├── SignCalendar.tsx          # 签到日历
│   ├── PointsLog.tsx             # 积分明细
│   ├── FavoriteList.tsx          # 收藏列表
│   └── NotificationList.tsx      # 通知列表
│
├── search/                       # 搜索组件
│   ├── SearchBar.tsx             # 搜索框 (带建议下拉)
│   ├── SearchSuggestions.tsx     # 搜索建议
│   ├── HotKeywords.tsx           # 热门搜索
│   ├── SearchFilter.tsx          # 搜索筛选面板
│   └── SearchHistory.tsx         # 搜索历史
│
├── marketing/                    # 营销组件
│   ├── CouponCard.tsx            # 优惠券卡片
│   ├── CouponClaimButton.tsx     # 领券按钮
│   ├── FlashSaleCard.tsx         # 秒杀商品卡片
│   ├── FlashCountdown.tsx        # 秒杀倒计时
│   ├── FlashProgress.tsx         # 秒杀进度条
│   └── Banner.tsx                # 轮播 Banner
│
├── chat/                         # 消息组件
│   ├── ChatWindow.tsx            # 聊天窗口
│   ├── ChatBubble.tsx            # 消息气泡
│   ├── ChatInput.tsx             # 消息输入框
│   ├── ConversationList.tsx      # 对话列表
│   ├── QuickReply.tsx            # 快捷回复
│   └── TypingIndicator.tsx       # 对方正在输入
│
├── ai/                           # AI 助手组件
│   ├── AiChatPanel.tsx           # AI 对话面板
│   ├── AiToolCallCard.tsx        # Tool 调用结果卡片
│   ├── AiProductRecommend.tsx    # AI 推荐商品卡片
│   └── AiConversationHistory.tsx # AI 对话历史列表
│
├── auth/                         # 认证组件
│   ├── LoginForm.tsx             # 登录表单
│   ├── RegisterForm.tsx          # 注册表单
│   ├── SmsCodeInput.tsx          # 短信验证码输入
│   ├── CaptchaWidget.tsx         # 人机验证组件
│   └── PasswordResetForm.tsx     # 找回密码表单
│
├── shop/                         # 店铺组件
│   ├── ShopHeader.tsx            # 店铺头部信息
│   ├── ShopInfoCard.tsx          # 店铺信息卡片
│   ├── FollowButton.tsx          # 关注按钮
│   └── ShopProductList.tsx       # 店铺商品列表
│
└── shared/                       # 通用基础组件
    ├── GlassCard.tsx             # 毛玻璃卡片
    ├── GlassButton.tsx           # 毛玻璃按钮
    ├── GlassModal.tsx            # 毛玻璃弹窗
    ├── GlassInput.tsx            # 毛玻璃输入框
    ├── Skeleton.tsx              # 骨架屏
    ├── EmptyState.tsx            # 空状态
    ├── ErrorState.tsx            # 错误状态
    ├── InfiniteScroll.tsx        # 无限滚动
    ├── ImageWithBlur.tsx         # 渐进式图片加载
    └── ScrollReveal.tsx          # 滚动渐显动画
```

### 6.2 页面级组件组装

```tsx
// 示例: 商城首页 page.tsx
// app/(marketplace)/page.tsx

import { Suspense } from 'react';
import { Header, Footer } from '@icedmall/ui/layout';
import { Banner } from '@icedmall/ui/marketing';
import { CategoryEntry } from './_components/CategoryEntry';
import { FeaturedProducts } from './_components/FeaturedProducts';
import { NewArrivals } from './_components/NewArrivals';
import { ActivityZone } from './_components/ActivityZone';

export default function MarketplacePage() {
  return (
    <>
      <Header />
      <main>
        {/* Banner: SSG, no JS needed */}
        <Banner />

        {/* 分类入口: Client Component for horizontal scroll */}
        <Suspense fallback={<Skeleton h={120} />}>
          <CategoryEntry />
        </Suspense>

        {/* 精选推荐: Server Component + ISR */}
        <Suspense fallback={<Skeleton h={320} />}>
          <FeaturedProducts />
        </Suspense>

        {/* 新品: Server Component + ISR */}
        <NewArrivals />

        {/* 活动专区: Client Component for countdown */}
        <Suspense fallback={<Skeleton h={200} />}>
          <ActivityZone />
        </Suspense>
      </main>
      <Footer />
    </>
  );
}
```

### 6.3 后端管理组件 (Ant Design)

```
apps/admin/ + apps/seller/

├── layout/
│   └── ProLayout.tsx              # Ant Design ProLayout 封装
│
├── dashboard/
│   ├── StatCard.tsx               # 统计卡片
│   ├── OrderChart.tsx             # 订单趋势图
│   └── TopProducts.tsx            # 热销榜
│
├── products/
│   ├── ProductTable.tsx           # ProTable 商品列表
│   ├── ProductForm.tsx            # 商品编辑表单
│   └── SkuManager.tsx             # SKU 管理
│
├── orders/
│   ├── OrderTable.tsx             # 订单列表
│   ├── OrderDetail.tsx            # 订单详情
│   └── ShipForm.tsx               # 发货表单
│
└── settings/
    ├── ShopSettingsForm.tsx       # 店铺设置
    └── RolePermissionTree.tsx     # 角色权限树
```

---

## 七、状态管理架构

### 7.1 Zustand Stores (客户端状态)

```typescript
// 认证状态
interface AuthStore {
  user: UserInfo | null;
  isAuthenticated: boolean;
  login: (tokens: OAuth2TokenResp) => void;
  logout: () => void;
  refreshSession: () => Promise<void>;
}

// 购物车状态 (乐观更新)
interface CartStore {
  items: CartItem[];
  selectedIds: Set<number>;
  totalPrice: number;
  selectedPrice: number;
  addItem: (skuId: number, quantity: number) => Promise<void>;
  updateQuantity: (skuId: number, qty: number) => Promise<void>;
  removeItem: (skuId: number) => Promise<void>;
  toggleSelected: (skuId: number) => void;
  toggleAll: (selected: boolean) => void;
}

// UI 状态
interface UIStore {
  isCartDrawerOpen: boolean;
  isSearchOpen: boolean;
  theme: 'light' | 'dark' | 'auto';
  toggleCartDrawer: () => void;
  setSearchOpen: (open: boolean) => void;
}

// AI 对话状态
interface AiStore {
  conversations: ConversationSummary[];
  activeConversationId: string | null;
  isStreaming: boolean;
  setActiveConversation: (id: string) => void;
  addConversation: (conv: ConversationSummary) => void;
}
```

### 7.2 TanStack Query Hooks (服务端状态)

```typescript
// packages/api/hooks/

// 商品
useProducts(filters: ProductFilters)       // GET /api/item/product/page
useProduct(id: number)                      // GET /api/item/product/{id}
useProductDetail(id: number)               // GET /api/item/product/{id}/detail

// 购物车
useCart()                                   // GET /api/cart
useAddToCart()                              // POST /api/cart/item
useUpdateCartItem()                         // PUT /api/cart/item

// 订单
useOrders(params: OrderPageParams)          // GET /api/trade/order/page
useOrder(orderNo: string)                   // GET /api/trade/order/{orderNo}
useCreateOrder()                            // POST /api/trade/order

// 支付
usePayOrder(orderNo: string)               // POST /api/pay/order/{orderNo}
usePayStatus(orderNo: string)              // GET /api/pay/order/{orderNo}/status

// 搜索
useSearch(keyword: string)                 // GET /api/search/product
useHotKeywords()                            // GET /api/search/hot
useSearchSuggestions(keyword: string)      // GET /api/search/suggest

// 优惠券
useAvailableCoupons()                      // GET /api/coupon/available
useCouponTemplates()                        // GET /api/coupon/template
useClaimCoupon()                            // POST /api/coupon/claim

// 秒杀
useFlashSales()                             // GET /api/flash
useFlashBuy()                               // POST /api/flash/buy

// AI
useAiChat()                                 // POST /api/ai/chat (useChat from Vercel AI SDK)
useConversations()                          // GET /api/ai/conversations
```

---

## 八、转化路径优化

### 8.1 加购路径

| 场景 | 入口 | 步骤 | 优化目标 |
|------|------|------|---------|
| 列表加购 | 商品列表/搜索 | 悬停卡片 → 点击加购按钮 → Toast 确认 → 继续浏览 | 1 步，不离开列表 |
| 详情加购 | 商品详情 | 选择规格 → 调整数量 → 点击加购 → 购物车数量更新 | 选择规格是必须步骤 |
| 立即购买 | 商品详情 | 选择规格 → 点击立即购买 → 跳转结算 | 跳过购物车 |

### 8.2 结算漏斗优化

```
进入结算 (100%)
    │
    ▼
选择地址 (98%)  ─── 2% 离开去新增地址
    │               (地址选择应在结算页内完成，不跳转)
    ▼
选择优惠券 (85%) ─── 13% 去领券中心
    │               (结算页内展示可用券，一键选用)
    ▼
提交订单 (80%)  ─── 5% 犹豫/价格敏感
    │               (显示"库存紧张"、"xxx人已购买"增加紧迫感)
    ▼
发起支付 (78%)  ─── 2% 支付方式不可用
    │               (提前展示可用支付方式)
    ▼
支付成功 (72%)
```

### 8.3 关键交互指标

| 指标 | 目标 | 衡量 |
|------|------|------|
| 加购率 (列表页) | ≥ 8% | add_to_cart / product_impression |
| 加购率 (详情页) | ≥ 25% | add_to_cart / product_view |
| 结算发起率 | ≥ 60% | begin_checkout / cart_view |
| 下单转化率 | ≥ 80% | place_order / begin_checkout |
| 支付成功率 | ≥ 90% | payment_success / initiate_payment |
| 整体转化率 | ≥ 3% | payment_success / marketplace_visit |

---

## 九、错误与边界状态

### 9.1 全局状态覆盖

每个数据展示组件必须覆盖以下状态：

```
┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
│ Loading  │   │  Empty   │   │  Error   │   │  Data    │
│ 骨架屏    │   │ 空状态   │   │ 错误+重试 │   │ 正常渲染  │
└──────────┘   └──────────┘   └──────────┘   └──────────┘
```

### 9.2 关键空状态文案

| 场景 | 空状态提示 | 行动引导 |
|------|-----------|---------|
| 空购物车 | "购物车是空的" | [去逛逛] |
| 无订单 | "还没有订单" | [探索好物] |
| 无收藏 | "还没有收藏商品" | [发现好物] |
| 无优惠券 | "还没有优惠券" | [领券中心] |
| 搜索无结果 | "未找到相关商品" | 展示热门搜索关键词 |
| 无收货地址 | "还没有收货地址" | [新增地址] |

---

## 十、移动端适配策略

| 断点 | 宽度 | 布局调整 |
|------|------|---------|
| Mobile | < 640px | 单列，底部 Tab 导航，抽屉式菜单 |
| Tablet | 640-1024px | 双列商品网格，侧边栏折叠 |
| Desktop | > 1024px | 多列，完整导航，悬浮购物车 |

移动端特殊处理：
- 商品图片点击放大全屏 (swipeable gallery)
- 购物车使用底部抽屉 (Bottom Sheet)
- 结算页单步表单 (非左右分栏)
- 底部 Tab 导航: 首页 · 分类 · 购物车 · 消息 · 我的

---

> **下一文档**: [17-Frontend-Design-System](./17-Frontend-Design-System.md) — 色彩、字体、组件视觉规范、动效系统
