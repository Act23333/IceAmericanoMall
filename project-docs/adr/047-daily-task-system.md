# ADR-047: 每日任务体系 —— 表驱动任务定义、每日幂等完成、积分单出口与旁路触发

## 背景

P2 增长线需要「做任务得积分」的任务体系拉动日活。积分基建早已齐备：`points_log` 流水（marketing）+ `PointsManager.addPoints` 单出口 + 既有积分来源（签到 1 / 下单奖励 2 / 新人奖励）——但任务本身无模型：运营无法定义任务，用户无任务中心，行为完成无记录可查。本 ADR 落地 Phase 1 最小闭环：**每日任务**（确认收货 / 发表评价），框架对后续任务类型（新手任务、周期任务）开放。

## 决策（2026-10-05）

### 1. 任务模型：表驱动定义 + 每日幂等记录（marketing 域，直接入 Initialize.sql 快照）

- `task_def`（`uk_code`）：code / name / type（1=每日重置，Phase 1 唯一值）/ reward_points / status。**表驱动而非枚举硬编码**——运营加任务=加一行 seed（按 ADR-045 口径直接改快照），代码零改动；管理端 CRUD 留 roadmap。
- `task_record`（`uk_user_task_date(user_id, task_code, biz_date)`）：**每日幂等锚点**——同一用户同一任务同一业务日最多完成一次；`biz_date` 由服务端按当天计算，调用方不可传（防伪造日期刷分）。
- seed：`DAILY_ORDER`（每日完成 1 笔订单确认，+20）/ `DAILY_REVIEW`（每日发表 1 条评价，+10）。

### 2. 完成与发奖：单端点收口，积分走单出口，同事务

- I49 `POST /internal/task/complete { userId, taskCode }`：任务定义存在且启用 → 插 `task_record`（命中唯一键 = 重复完成，捕获回落返回 `completed=false`）→ `PointsManager.addPoints(userId, reward, type=3 任务奖励, source=task:{code})`——**记录与积分同一本地事务**（纯本地写允许 @Transactional），积分流水沿用既有单出口与审计口径。
- 返回 `TaskCompleteResultCO { completed, rewardPoints }`：首次=true+实发积分；重复/任务停用/任务不存在=false+0（不向调用方抛业务异常——幂等语义，参照 ADR-035 幂等回落纪律）。

### 3. 触发点与降级定类：事务外 fail-open 旁路

| 触发 | 调用方落点 | 任务 |
| --- | --- | --- |
| 确认收货 | trade `OrderLifecycleManager`（member growth / 积分倍率同一编排点之后） | `DAILY_ORDER` |
| 评价落库 | item `ReviewManager`（与既有通知钩子同位置口径；若处于事务内用 afterCommit 注册） | `DAILY_REVIEW` |

两处均**事务外 fail-open**（ia-api `TaskClient` fallback 返回 `{false, 0}` + warn）：任务奖励是体验激励，绝不影响交易/评价主流程——与触达接线（ADR-042）、向量摄取（ADR-046）同一旁路口径，与 I43/I44 资金/定价 fail-closed 刻意对比。

### 4. 防刷边界（Phase 1 口径）

- 每日上限 = 任务奖励额（uk 每日一次），刷分收益封顶（每日合计 +30 分）；
- 「删评重评」在记录删除场景下可于同日重复完成——Phase 1 接受（删除评价为既有能力且收益封顶），如后续开放删除频繁则将评价任务锚点升级为 `biz 维度`（uk 扩列）；
- `biz_date` 服务端计算、任务启用状态服务端校验，调用方只传 userId+taskCode。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 任务枚举硬编码 | 零表 | 运营调奖励值/加任务都要发版，违背表驱动初衷 |
| 任务奖励走独立流水不复用 points_log | 账目分域 | 积分出口分裂，余额聚合口径复杂化 |
| 完成记录无 biz_date（全周期一次） | 更简单 | 「每日任务」语义不成立 |
| MQ 事件化触发 | 解耦 | dev 关 MQ 断链；旁路直接 Feign fail-open 足够（ADR-042 同判） |
| **表驱动 + 每日 uk 幂等 + 单出口同事务 + 旁路触发（采纳）** | 运营可扩展、幂等单点、账目单源、主流程零风险 | 管理端 CRUD 未做（直接改 seed）；删评重评每日一次的刷分边界 |

## 后果

**正面**

- 任务体系从零到一：任务中心（M14）+ 幂等完成（I49）+ 表驱动扩展位；
- 积分账目仍单源（points_log），任务奖励流水可审计（source=task:{code}）；
- 触发旁路零风险，后续任务类型（新手/周期）只加 task_def 行 + 一个触发点。

**负面 / 风险**

- 管理端任务 CRUD 未做（Phase 1 seed 直改快照）；
- 删评重评同日可重复刷一次评价任务（收益封顶，Phase 1 接受）；
- 任务列表每次全量读 task_def + 当日记录（量级小，不加缓存）；
- 任务类型仅「每日」一种（type 列已预留，新手/周期任务待扩）。

## 日期

2026-10-05

## 参考

- `Implementation/back-end/marketing-service/src/main/java/org/icedamericanomall/marketing/app/task/`（TaskManager）
- `Implementation/back-end/trade-service/.../OrderLifecycleManager.java`（DAILY_ORDER 触发）、`item-service/.../ReviewManager.java`（DAILY_REVIEW 触发）
- `Implementation/back-end/ia-api/`（`TaskClient` fail-open / `TaskCompleteCO` / `TaskCompleteResultCO`）
- ADR-045（结构直接改快照）、ADR-042/046（旁路 fail-open 先例）、ADR-011（降级三分法）、ADR-035（幂等回落纪律）
