# 📦 小程序端 (Taro 4)

**状态**：占位项目，后期激活。

## 何时激活

- ✅ Web PWA 上线并验证 PMF 后
- ✅ 需要微信生态流量入口（搜索/附近/分享）
- ✅ 需要小程序特有能力（微信支付收银台优化、订阅消息）

## 技术方案

- **框架**：Taro 4 (React 语法)
- **目标平台**：微信小程序 → 支付宝小程序 → 抖音小程序
- **共享**：`@icedmall/api` 类型定义 + `@icedmall/utils` 工具函数

## 激活步骤

1. `pnpm install` — 安装 Taro CLI + 运行时
2. `taro init` — 初始化项目（替换当前占位）
3. 从 `apps/web` 迁移共享的 TanStack Query hooks
4. 适配小程序特有 API（`wx.login`、`wx.requestPayment` 等）
