# 📦 App 端 (React Native)

**状态**：可选占位项目。

## 当前策略

**PWA 先行**。`apps/web` 已配置 PWA manifest + Service Worker，用户可：
- 添加到手机主屏幕
- 离线浏览已缓存的商品页
- 接收推送通知（iOS 17.4+ 已支持）

## 何时激活此项目

PWA 在以下方面不够用时，再启动 React Native：
- 需要深度原生能力（蓝牙打印小票、NFC 刷卡）
- iOS 推送通知到达率不满足业务需求
- 应用商店分发（App Store / 各大安卓市场）
- 性能要求超过 Web 能力上限

## 技术方案

- **框架**：React Native 0.76+ (New Architecture)
- **共享**：`@icedmall/api` 类型 + `@icedmall/utils` 工具
- **注意**：RN 不能复用 `apps/web` 的 UI 组件（DOM vs Native View）
