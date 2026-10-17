/**
 * 常量 — 与后端 ErrorCode / enums 同步
 *
 * ⚠️ 订单状态（`OrderStatusEnum`）的映射已迁到独立模块 `./order-status.ts`：
 *   旧实现此处是 `ORDER_STATUS_TEXT: Record<string, string>`（键 = 枚举名）而调用方用
 *   **数字** `order.status` 索引 ⇒ 恒 `undefined`、页面退化成裸数字。数字连续性是后端的
 *   实现细节，不可作为前端映射依据 —— 新模块用 `Record<OrderStatusCode, string>` 钉死键域。
 */

/** 支付状态 */
export const PAY_STATUS = {
  PENDING: 'PENDING',
  PAID: 'PAID',
  CLOSED: 'CLOSED',
  REFUNDING: 'REFUNDING',
  REFUNDED: 'REFUNDED',
} as const;

/** 物流状态 */
export const LOGISTICS_STATUS = {
  PENDING: 'PENDING',
  SHIPPED: 'SHIPPED',
  DELIVERED: 'DELIVERED',
  RETURNED: 'RETURNED',
} as const;

/** 用户角色 */
export const USER_ROLE = {
  BUYER: 'BUYER',
  SELLER: 'SELLER',
  ADMIN: 'ADMIN',
} as const;
