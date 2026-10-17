/**
 * @icedmall/utils — 共享工具函数
 *
 * 提供：金额格式化、日期格式化、校验器、常量
 */

export * from './format';
export * from './coupon-face';
export * from './validate';
export * from './constants';
// SPU 详情回链的唯一构造出口（`spuHref` / `hasSpuKey`）—— 缺失业务键时返回 null，不渲染空链接
export * from './spu-link';
