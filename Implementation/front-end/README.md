# 冰美商城 — 前端 Monorepo

## 结构

```
front-end/
├── apps/
│   ├── web/           ✅ 用户端 H5 (Next.js 15 + PWA) — 当前开发
│   ├── admin/         ✅ 平台管理后台 (Next.js + Ant Design 5) — 当前开发
│   ├── seller/        ✅ 商家后台 (Next.js + Ant Design 5) — 当前开发
│   ├── miniprogram/   📦 小程序 (Taro 4) — 后期激活
│   └── mobile/        📦 App (React Native) — PWA 不够用时激活
├── packages/
│   ├── api/           共享 API 类型 + TanStack Query hooks
│   ├── ui/            共享 UI 组件 (Shadcn/ui + 自定义)
│   ├── auth/          认证模块 (JWT + Middleware)
│   ├── utils/         工具函数 (格式化/校验/常量)
│   └── config/        共享配置 (ESLint/TSConfig/Tailwind)
├── pnpm-workspace.yaml
├── turbo.json
├── package.json
└── tsconfig.base.json
```

## 快速开始

```bash
# 安装依赖
pnpm install

# 开发 — 用户端 (localhost:3000)
pnpm dev:web

# 开发 — 管理后台 (localhost:3001)
pnpm dev:admin

# 开发 — 商家后台 (localhost:3002)
pnpm dev:seller

# 构建
pnpm build

# 测试
pnpm test
```
