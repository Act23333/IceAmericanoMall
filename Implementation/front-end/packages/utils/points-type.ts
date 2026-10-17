/**
 * 积分流水 `type` 取值域 + 展示口径 —— 逐值镜像后端 `points_log.type`
 *
 * 真源（三处逐字一致，任一处改动都必须同步本文件）：
 *   1. `Implementation/back-end/database/Initialize.sql:581`
 *      `type TINYINT NOT NULL COMMENT '1=签到, 2=下单, 3=任务, 4=兑换消耗, 5=过期'`
 *   2. marketing `domain/model/points/PointsLog.java:24`（同口径 javadoc）
 *   3. marketing `adapter/response/PointsLogVO.java:23`（出参口径）
 *
 * marketing-service **没有** `PointsTypeEnum`（`domain/enums/` 下无此类）—— type 是裸
 * `Integer`，写入口是 `PointsManager.addPoints(userId, points, type, source)`
 * （`/internal/points/add`，`InternalPointsController.DEFAULT_POINTS_TYPE = "2"`）。
 * 现存**实际写入**的取值（生产者，逐个核对过）：
 *
 * | type | 语义     | 生产者（文件:行）                                                        |
 * | :--: | :------- | :---------------------------------------------------------------------- |
 * | 1    | 签到     | marketing `app/sign/SignManager.java:22`（`POINTS_TYPE_SIGN`，发出 `:35`）|
 * | 2    | 下单     | trade `app/order/OrderLifecycleManager.java:45`（发出 `:119`）           |
 * | 2    | 下单（复用） | user `infrastructure/adapter/NewUserRewardServiceAdapter.java:27`（注册奖励，发出 `:40`）|
 * | 3    | 任务     | marketing `app/task/TaskManager.java:34`（发出 `:70`）                   |
 * | 4    | 兑换消耗 | **声明在案、当前无生产者**（域注释与 DDL 已登记，代码零命中）             |
 * | 5    | 过期     | **声明在案、当前无生产者**（同上）                                       |
 *
 * 硬规则（本模块存在的唯一理由）：
 *   1. 旧页面用 `log.type === 1 ? '获取积分' : '消费积分'` 猜映射 ⇒ `2`（下单）/ `3`（任务）
 *      这些**同样是获得**的流水被显示成「消费积分」，并且金额符号写死 `type === 1 ? '+'
 *      : '-'` ⇒ 下单奖励 +80 被渲染成 `-80`（**符号反了**）。
 *      符号的真源是 `points` 自身（契约：正=获得，负=消耗），**不是** type。
 *   2. type → 文案集中在本模块 `formatPointsLog()` 一个函数里，页面不得再自行 `=== 1` 判类。
 *   3. 表外取值（后端新增 type）→ **显示原始值**（`类型 9`），不空白、不写「未知」吞掉。
 */

/** 积分流水类型码 —— 逐值镜像 `points_log.type`。 */
export const POINTS_LOG_TYPE = {
  /** 1 — 签到 */
  SIGN_IN: 1,
  /** 2 — 下单（新用户注册奖励复用此码，具体来源看 `source`） */
  ORDER: 2,
  /** 3 — 任务 */
  TASK: 3,
  /** 4 — 兑换消耗（声明在案、当前无生产者） */
  EXCHANGE: 4,
  /** 5 — 过期（声明在案、当前无生产者） */
  EXPIRE: 5,
} as const;

/** 积分流水类型码联合类型（`1 | 2 | 3 | 4 | 5`）。 */
export type PointsLogType = (typeof POINTS_LOG_TYPE)[keyof typeof POINTS_LOG_TYPE];

/**
 * 类型码 → 中文文案（键域 = 后端取值）。
 * `Record<PointsLogType, string>` ⇒ 后端新增取值而本表漏补时**编译期报错**。
 */
export const POINTS_LOG_TYPE_LABEL: Record<PointsLogType, string> = {
  1: '签到',
  2: '下单',
  3: '任务',
  4: '兑换消耗',
  5: '过期',
};

/** 表外取值的统一前缀（后面拼**原始值**）。 */
const UNKNOWN_TYPE_PREFIX = '类型';

/** 原始入参 → 类型码；无法解释时返回 `null`。 */
function toPointsType(type: number | string | null | undefined): number | null {
  if (type == null || type === '') return null;
  if (typeof type === 'number') return Number.isFinite(type) ? type : null;
  const parsed = Number(type);
  return Number.isFinite(parsed) ? parsed : null;
}

/** 是否为已知类型码（类型守卫）。 */
export function isKnownPointsLogType(
  type: number | string | null | undefined,
): type is PointsLogType {
  const code = toPointsType(type);
  return code != null && code in POINTS_LOG_TYPE_LABEL;
}

/**
 * 类型码 → 文案（**所有**积分类型文案的唯一入口）。
 *
 * - 已知码 → `签到` / `下单` / `任务` / `兑换消耗` / `过期`
 * - 表外码 → `类型 9`（**带原始值**，让「后端加了 type、前端没跟上」肉眼可见）
 * - `null` / 空串 → `积分变动`（数据本身缺失）
 */
export function pointsLogTypeLabel(type: number | string | null | undefined): string {
  const code = toPointsType(type);
  if (code == null) return '积分变动';
  const known = POINTS_LOG_TYPE_LABEL[code as PointsLogType];
  return known ?? `${UNKNOWN_TYPE_PREFIX} ${type}`;
}

/** 展示所需的全部派生字段。 */
export interface PointsLogDisplay {
  /** 类型文案（`签到` / `下单` / … / 表外码 `类型 9`）。 */
  typeText: string;
  /** 是否为表外类型码。 */
  isUnknownType: boolean;
  /** 是否入账（`points > 0`；**由 `points` 符号判定，不由 type 判定**）。 */
  isCredit: boolean;
  /** 带符号的积分数值文案：`+80` / `-50`；缺失 → `—`。 */
  signedPointsText: string;
  /** 无符号绝对值（给大号数字单独渲染用）；缺失 → `—`。 */
  absPointsText: string;
  /** 主文案：优先 `source`（更具体），缺失回落到类型文案。 */
  primaryText: string;
}

/**
 * 一条积分流水 → 展示口径（**唯一出口**，页面不得再自行判类/判符号）。
 *
 * 入参用**线上字段名**（`type` / `points` / `source`）—— 与 `PointsLogVO` 逐字对应，
 * 避免调用方再多一层改名映射（改名本身就是新的静默失效点）。
 */
export function formatPointsLog(
  log: { type?: number | string | null; points?: number | null; source?: string | null },
): PointsLogDisplay {
  const typeText = pointsLogTypeLabel(log.type);
  const points = log.points;
  const isCredit = typeof points === 'number' && points > 0;
  const hasPoints = typeof points === 'number' && Number.isFinite(points);
  const signedPointsText = hasPoints
    ? `${isCredit ? '+' : points < 0 ? '-' : ''}${Math.abs(points)}`
    : '—';
  const absPointsText = hasPoints ? String(Math.abs(points)) : '—';
  const source = log.source?.trim();
  return {
    typeText,
    isUnknownType: !isKnownPointsLogType(log.type),
    isCredit,
    signedPointsText,
    absPointsText,
    primaryText: source && source !== '' ? source : typeText,
  };
}
