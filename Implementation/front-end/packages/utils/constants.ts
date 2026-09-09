/**
 * 常量 — 与后端 ErrorCode / enums 同步
 */

/** 订单状态 (与后端 OrderStateMachine 一致) */
export const ORDER_STATUS = {
  PENDING_PAY: 'PENDING_PAY',
  PENDING_SHIP: 'PENDING_SHIP',
  PENDING_RECEIPT: 'PENDING_RECEIPT',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
  PENDING_REVIEW: 'PENDING_REVIEW',
} as const;

export type OrderStatus = (typeof ORDER_STATUS)[keyof typeof ORDER_STATUS];

/** 订单状态中文映射 (字符串键) */
export const ORDER_STATUS_TEXT: Record<string, string> = {
  PENDING_PAY: '待付款',
  PENDING_SHIP: '待发货',
  PENDING_RECEIPT: '待收货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  PENDING_REVIEW: '待评价',
};

/** 订单状态中文映射 (数字键，与后端 status Integer 对齐) */
export const ORDER_STATUS_NUMERIC: Record<number, string> = {
  1: '待付款',
  2: '待发货',
  3: '待收货',
  4: '已完成',
  5: '已取消',
  6: '待评价',
};

/** 订单状态颜色映射 (数字键 → Tailwind class) */
export const ORDER_STATUS_COLOR: Record<number, string> = {
  1: 'text-amber bg-amber/10',
  2: 'text-info bg-info/10',
  3: 'text-info bg-info/10',
  4: 'text-accent bg-accent/10',
  5: 'text-text-tertiary bg-warm-gray-100',
  6: 'text-accent bg-accent/10',
};

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
