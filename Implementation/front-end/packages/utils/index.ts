/**
 * @icedmall/utils — 共享工具函数
 *
 * 提供：金额格式化、日期格式化、校验器、常量、枚举取值域映射
 */

export * from './format';
export * from './coupon-face';
export * from './validate';
export * from './constants';
// 订单状态文案（真源 trade-service `OrderStatusEnum`；禁止用数字下标索引字符串表）
export * from './order-status';
// 站内信 type 取值域 + 标签页分组（真源 = notification 的 4 个生产方常量，不是表注释）
export * from './notification-type';
// 积分流水 type 文案（真源 `points_log.type`；正负号由 points 自身判定，不由 type 判定）
export * from './points-type';
// SPU 详情回链的唯一构造出口（`spuHref` / `hasSpuKey`）—— 缺失业务键时返回 null，不渲染空链接
export * from './spu-link';
