/**
 * 订单状态文案 —— 以后端 `OrderStatusEnum` 为**唯一真相源**
 *
 * 真源：`Implementation/back-end/trade-service/.../trade/domain/enums/OrderStatusEnum.java`
 * （`@EnumValue code` + `desc`，落库列 `orders.status` TINYINT）：
 *
 * | code | 枚举名            | desc   |
 * | :--: | :---------------- | :----- |
 * | 1    | PENDING_PAYMENT   | 待付款 |
 * | 2    | PENDING_SHIPMENT  | 待发货 |
 * | 3    | PENDING_RECEIPT   | 待收货 |
 * | 4    | COMPLETED         | 已完成 |
 * | 5    | CANCELLED         | 已取消 |
 * | 6    | PENDING_REVIEW    | 待评价 |
 *
 * 硬规则（本模块存在的唯一理由）：
 *   1. **禁止用数字下标索引一张「状态名 → 文案」的字符串表**。旧实现 `ORDER_STATUS_TEXT`
 *      是 `Record<string, string>`，键是 `PENDING_PAY` 这类**枚举名**，而调用点写的是
 *      `ORDER_STATUS_TEXT[order.status]`（`status` 是数字 1..6）⇒ 恒 `undefined`
 *      ⇒ 页面上「待付款」全部退化成裸数字「1」，且**不报错**。数字连续性是后端的实现
 *      细节，不能当前端的映射依据。
 *   2. 映射表键域是**数字枚举码**，类型为 `Record<OrderStatusCode, string>`：后端新增状态而
 *      本表漏补时 **编译期报错**（不是运行期静默变空）。
 *   3. 运行期遇到表外取值（历史脏数据 / 后端先上线、前端未发版）→ 经 `orderStatusText()`
 *      显示**原始值**（`状态 7`），**绝不显示空白、绝不猜测**。
 */

/**
 * 订单状态码 —— 逐值镜像 `OrderStatusEnum`。
 *
 * 枚举名与后端**逐字一致**（旧常量写的是 `PENDING_PAY` / `PENDING_SHIP`，与后端不符，已更正）。
 */
export const ORDER_STATUS_CODE = {
  PENDING_PAYMENT: 1,
  PENDING_SHIPMENT: 2,
  PENDING_RECEIPT: 3,
  COMPLETED: 4,
  CANCELLED: 5,
  PENDING_REVIEW: 6,
} as const;

/** 订单状态码联合类型（`1 | 2 | 3 | 4 | 5 | 6`）。 */
export type OrderStatusCode = (typeof ORDER_STATUS_CODE)[keyof typeof ORDER_STATUS_CODE];

/**
 * 状态码 → 中文文案（键域 = 数字枚举码）。
 * `Record<OrderStatusCode, string>` ⇒ 缺任一状态码即编译期报错。
 */
export const ORDER_STATUS_TEXT: Record<OrderStatusCode, string> = {
  1: '待付款',
  2: '待发货',
  3: '待收货',
  4: '已完成',
  5: '已取消',
  6: '待评价',
};

/** 状态码 → 徽标配色（设计 token class，键域同上；取值见 `17-Frontend-Design-System` §2）。 */
export const ORDER_STATUS_COLOR: Record<OrderStatusCode, string> = {
  1: 'text-amber bg-amber/10',
  2: 'text-info bg-info/10',
  3: 'text-info bg-info/10',
  4: 'text-accent bg-accent/10',
  5: 'text-text-tertiary bg-warm-gray-100',
  6: 'text-accent bg-accent/10',
};

/** 表外取值的统一前缀（后面拼**原始值**，不吞掉）。 */
const UNKNOWN_STATUS_PREFIX = '状态';

/** 表外取值的配色（中性，不伪装成已知状态的语义色）。 */
const UNKNOWN_STATUS_COLOR = 'text-text-tertiary bg-warm-gray-100';

/** 原始入参 → 状态码；无法解释时返回 `null`（`"3"` 这类数字串也接受——防御历史报文）。 */
function toStatusCode(status: number | string | null | undefined): number | null {
  if (status == null || status === '') return null;
  if (typeof status === 'number') return Number.isFinite(status) ? status : null;
  const parsed = Number(status);
  return Number.isFinite(parsed) ? parsed : null;
}

/** 是否为已知状态码（类型守卫；`null` / 表外取值 → `false`）。 */
export function isKnownOrderStatus(
  status: number | string | null | undefined,
): status is OrderStatusCode {
  const code = toStatusCode(status);
  return code != null && code in ORDER_STATUS_TEXT;
}

/**
 * 订单状态 → 中文文案（**所有**订单状态展示的唯一入口）。
 *
 * - 已知状态码 → `待付款` / `待发货` / …
 * - 表外取值 → `状态 7`（**带原始值**，让「后端加了状态、前端没跟上」肉眼可见）
 * - `null` / 空串 → `—`（数据本身缺失，非状态未知）
 */
export function orderStatusText(status: number | string | null | undefined): string {
  const code = toStatusCode(status);
  if (code == null) return '—';
  const known = ORDER_STATUS_TEXT[code as OrderStatusCode];
  return known ?? `${UNKNOWN_STATUS_PREFIX} ${status}`;
}

/** 订单状态 → 徽标配色（表外取值走中性色，不冒充任何已知状态的语义）。 */
export function orderStatusColorClass(status: number | string | null | undefined): string {
  const code = toStatusCode(status);
  if (code == null) return UNKNOWN_STATUS_COLOR;
  return ORDER_STATUS_COLOR[code as OrderStatusCode] ?? UNKNOWN_STATUS_COLOR;
}
