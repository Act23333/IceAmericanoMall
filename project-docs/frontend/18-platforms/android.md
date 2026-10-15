# 18-platforms/android — Android 平台实施方案（占位骨架）

> 归属域: frontend | Owner: doc-engineer | 更新触发条件: Android 平台进入排期并开工时
>
> 上级文档: [18-Frontend-Implementation.md](../18-Frontend-Implementation.md)（总览 —— Phase 模型、平台优先级、跨平台共享物、全局 DoD）
> 节结构来源: 多平台前端文档重构方案 §6.2（每个平台一篇，八节固定）

> ⚠️ **本文是占位骨架，尚无事实依据。**
>
> **仓库现状（已核实）**：本仓库**无独立 Android 工程**。Android 当前唯一的候选承载路径是占位的 `apps/mobile`（React Native，1 文件 / 0 行，其自述见 `Implementation/front-end/apps/mobile/README.md`）。是否另立原生 Compose 工程**未决策**。
> 按仓库硬规范「不编造、核实不了标注未核实」，本平台在开工前**不填任何实施细节**——
> 只记录已核实的仓库现状与待决策项。**填入正式内容的前置条件见 §九。**

---

## 〇、平台状态（已核实部分）

| 项 | 值 | 取证 |
| --- | --- | --- |
| 状态 | ⚪ 未开工 | — |
| 技术栈 | **未定**（未决策，勿假定） | — |
| 仓库位置 | 无对应工程（见上方「仓库现状」） | — |

---

## 一、环境准备

> **TODO（开工时填写）**：SDK、IDE、模拟器/真机、开发者账号。

- [ ] TODO：列出该平台必需的工具链与版本下限
- [ ] TODO：列出必需的开发者账号与权限（账号归属、费用、审批周期）
- [ ] TODO：列出本地调试所需的设备/模拟器清单（与 [11-Test-Strategy.md](../../quality/11-Test-Strategy.md) 的设备矩阵对齐，不在此复制）

---

## 二、脚手架与目录

> **TODO（开工时填写）**：该平台的目录结构。
>
> ⚠️ **不得沿用 Web 的目录结构**（Next.js 的 `app/` 路由约定、React 组件目录组织均不适用于其他生态）。
> 该平台目录应遵循本生态自身的惯用组织方式，并登记与 `packages/*` 共享层的边界。

---

## 三、阶段拆解

> **TODO（开工时填写）**：复用总览 §三 的 Phase 模型（Phase 1-6 定义跨平台通用），填本平台的具体任务与工期。
>
> Phase 定义见[总览 §三](../18-Frontend-Implementation.md)；**不要在此重新定义 Phase**。

| Phase | 主题 | 工期 | 状态 |
| --- | --- | --- | --- |
| 1 | TODO | TODO | ⚪ 未开工 |
| 2 | TODO | TODO | ⚪ 未开工 |
| 3 | TODO | TODO | ⚪ 未开工 |
| 4 | TODO | TODO | ⚪ 未开工 |
| 5 | TODO | TODO | ⚪ 未开工 |
| 6 | TODO | TODO | ⚪ 未开工 |

---

## 四、CI/CD

> **TODO（开工时填写）**：构建产物、制品库、自动化测试接入方式。
>
> ⚠️ 该平台的构建**通常无法在通用 Linux runner 上完成**（需要相应 SDK 与签名环境）；
> 需明确「哪些检查能在 GitHub Actions 上跑、哪些必须在本机或专用 runner 上跑」。

---

## 五、签名与发布

> **TODO（开工时填写）**：证书、签名、商店上架流程与审核周期。

---

## 六、灰度与回滚

> **TODO（开工时填写）**：灰度手段与回滚边界。
>
> ⚠️ **这是平台间差异最大的一节**：Web 可随时回滚（见 [web.md](./web.md) §七），
> 而分发型平台的既有版本**收不回**。开工时必须先定「服务端开关优先」的降级策略，
> 再定「客户端版本」的发布节奏。

---

## 七、平台 DoD

> **TODO（开工时填写）**：叠加在[总览 §七](../18-Frontend-Implementation.md) 全局 DoD 之上的本平台专属完成定义。

| # | 检查项 | 校验方式 |
| --- | --- | --- |
| 1 | TODO | TODO |

---

## 八、风险

| 风险 | 概率 | 影响 | 缓解措施 |
| --- | --- | --- | --- |
| TODO | — | — | — |

---

## 九、开工前置（填入正式内容的条件）

> 本文从占位骨架转为实稿的准入条件 —— 以下全部完成前，本文保持骨架状态：

- [ ] TODO：该平台的技术选型已在 [15-Front-End-Technology-Selection.md](../15-Front-End-Technology-Selection.md) 的**平台矩阵**中定稿（含生态/语言框架/构建/包管理/最低支持版本）
- [ ] TODO：设计系统的[平台适配差异](../17-Frontend-Design-System.md)已登记该平台的 token 注入方式与偏离原因
- [ ] TODO：编码规范已补入 [08-agents.md](../../collaboration/08-agents.md) §十（该生态的命名与惯用法）
- [ ] TODO：测试的设备矩阵与执行流程已补入 [11-Test-Strategy.md](../../quality/11-Test-Strategy.md) / [19-Test-Flow-Guide.md](../../quality/19-Test-Flow-Guide.md)
- [ ] TODO：平台优先级与排期已定

---

> **上级文档**: [18-Frontend-Implementation.md](../18-Frontend-Implementation.md)（总览）
> **平台轴同层**: [web](./web.md) · [desktop](./desktop.md) · [android](./android.md) · [ios](./ios.md) · [miniprogram](./miniprogram.md)
