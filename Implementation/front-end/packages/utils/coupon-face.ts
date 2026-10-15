/**
 * 券面展示工具 —— 按 `discount_type` 正确解释 `value` 的单位
 *
 * 契约：`project-docs/contract/05-API-Specification.md` §4.10.1（`UserCouponVO` / `CouponVO`）。
 *
 * | `discount_type` | 含义                     | `value` 单位                 |
 * | :-------------: | :----------------------- | :--------------------------- |
 * | `1`             | FIXED 满减               | **分**（`3000` = 30 元）     |
 * | `2`             | PERCENTAGE 折扣          | **百分比**（`85` = 8.5 折）  |
 * | `3`             | CASH_COUPON 代金券       | **分**                       |
 * | `null` / 未知    | 券模板缺失或读模型降级    | 不可解释 → 降级「优惠券」     |
 *
 * 硬规则（本模块存在的唯一理由）：
 *   1. `value` 的单位**随** `discount_type` 变化 → 任何券面展示都必须经本模块。
 *      页面里**不得**直接写 `formatPrice(value)` / `${value}折`：前者把折扣券的 `85`
 *      当成 85 分渲染成 `-¥0.85`，后者把 `85` 渲染成「85 折」。
 *   2. `discount_type` 为 `null` / 未知时**必须降级**为「优惠券」文案，
 *      **绝不可按满减口径渲染**（fail-open 的语义是「不猜」，不是「按满减兜底」）。
 *   3. `coupon.type` 自 V4.0 起 DEPRECATED（`UserCouponVO` 已不投影）→ 本模块只看
 *      `discount_type`，调用方也不得传 `type` 进来。
 *   4. 金额全程**整数分**，只在最终格式化时经 `formatPrice` 转成两位小数字符串；
 *      百分比 → 折数用整数取位运算，**不做浮点运算**。
 *
 * 边界（见各函数注释）：`value = 100`（折扣券）→ `10折`（即不打折，照实渲染）；
 * `value = null`（折扣券）→ 不渲染面额，降级「优惠券」；`min_amount` 为 `0`/`null` → 「无门槛」。
 */

import { formatPrice } from './format';

/** 折扣类型 FIXED：满减，`value` 为**分**（契约 §4.10.1）。 */
export const COUPON_DISCOUNT_TYPE_FIXED = 1;

/** 折扣类型 PERCENTAGE：折扣，`value` 为**百分比**（`85` = 8.5 折）。 */
export const COUPON_DISCOUNT_TYPE_PERCENTAGE = 2;

/** 折扣类型 CASH_COUPON：代金券，`value` 为**分**。 */
export const COUPON_DISCOUNT_TYPE_CASH_COUPON = 3;

/** 券面不可解释时的统一降级文案（`discount_type` 缺失 / 未知）。 */
export const COUPON_FACE_FALLBACK_TEXT = '优惠券';

/** 百分比基数：`100` = 不打折（折扣券 `value = 100` 即 10 折）。 */
const PERCENT_BASE = 100;

/** 券面输入（前端内部标识符保持 camelCase；可从 `UserCouponVO` / `CouponVO` 显式取值传入）。 */
export interface CouponFaceInput {
  /** 契约 `discount_type`：`1`-满减 / `2`-折扣 / `3`-代金券；`null` = 未知（模板缺失 / 降级）。 */
  discountType: number | null | undefined;
  /** 券面值：满减 / 代金券为**分**，折扣为**百分比**；`null` = 券面缺失。 */
  value: number | null | undefined;
  /** 使用门槛（分）；`null` / `0` 表示无门槛。 */
  minAmount?: number | null;
}

/** 券面展示结果（各页面按需取用，避免三处各写一遍类型判定）。 */
export interface CouponFaceDisplay {
  /** 券面不可解释（`discount_type` 缺失 / 未知，或折扣券缺 `value`）→ 已降级。 */
  isUnknown: boolean;
  /** 是否折扣券（`value` 按百分比解释，如 `85` → `8.5折`）。 */
  isPercentage: boolean;
  /** 面额文案（含单位）：`¥30.00` / `8.5折` / `优惠券`。 */
  amountText: string;
  /** 抵扣文案：`-¥30.00` / `8.5折` / `优惠券`（折扣券不编造金额，直接给折数）。 */
  deductionText: string;
  /** 券名缺失时的兜底券名：`满¥200.00减¥30.00` / `8.5折券` / `代金券¥30.00` / `优惠券`。 */
  fallbackName: string;
  /** 门槛文案：`满¥200.00可用` / `无门槛`。 */
  thresholdText: string;
}

/**
 * 门槛文案。
 * `min_amount > 0` → `满¥200.00可用`；`0` / `null` → `无门槛`（契约 §4.10.1 门槛为分）。
 */
export function formatCouponThreshold(minAmount: number | null | undefined): string {
  return minAmount != null && minAmount > 0 ? `满¥${formatPrice(minAmount)}可用` : '无门槛';
}

/**
 * 折扣百分比 → 折数文案（整数运算，无浮点）。
 * `85` → `8.5`；`80` → `8`；`95` → `9.5`；`100` → `10`（10 折 = 不打折，照实渲染不隐藏）。
 */
export function percentageToDiscountText(percent: number): string {
  const whole = Math.trunc(percent / 10);
  const tenth = Math.abs(Math.trunc(percent)) % 10;
  return tenth === 0 ? String(whole) : `${whole}.${tenth}`;
}

/**
 * 券面 → 展示文案（**所有**券面展示的唯一入口）。
 *
 * - `1` FIXED / `3` CASH_COUPON：`value` 按**分**解释 → `¥30.00` / `-¥30.00`
 * - `2` PERCENTAGE：`value` 按**百分比**解释 → `8.5折`（`100` → `10折`）；
 *   `value` 为 `null` → 不渲染面额，降级「优惠券」
 * - `null` / 未知类型 → 降级「优惠券」（**绝不按满减渲染**）
 */
export function formatCouponFace(input: CouponFaceInput): CouponFaceDisplay {
  const { discountType, value, minAmount } = input;
  const thresholdText = formatCouponThreshold(minAmount);

  const isKnownType =
    discountType === COUPON_DISCOUNT_TYPE_FIXED ||
    discountType === COUPON_DISCOUNT_TYPE_PERCENTAGE ||
    discountType === COUPON_DISCOUNT_TYPE_CASH_COUPON;

  // 未知类型，或折扣券缺 value（不可解释）→ 统一降级，不猜口径
  if (!isKnownType || value == null) {
    return {
      isUnknown: true,
      isPercentage: false,
      amountText: COUPON_FACE_FALLBACK_TEXT,
      deductionText: COUPON_FACE_FALLBACK_TEXT,
      fallbackName: COUPON_FACE_FALLBACK_TEXT,
      thresholdText,
    };
  }

  if (discountType === COUPON_DISCOUNT_TYPE_PERCENTAGE) {
    const discountText = `${percentageToDiscountText(value)}折`;
    return {
      isUnknown: false,
      isPercentage: true,
      amountText: discountText,
      deductionText: discountText,
      fallbackName: `${discountText}券`,
      thresholdText,
    };
  }

  const amountText = `¥${formatPrice(value)}`;
  const fallbackName =
    discountType === COUPON_DISCOUNT_TYPE_CASH_COUPON
      ? `代金券${amountText}`
      : minAmount != null && minAmount > 0
        ? `满¥${formatPrice(minAmount)}减${amountText}`
        : `立减${amountText}`;

  return {
    isUnknown: false,
    isPercentage: false,
    amountText,
    deductionText: `-${amountText}`,
    fallbackName,
    thresholdText,
  };
}

/**
 * 券抵扣金额**展示预估**（分）—— 仅用于结算页渲染「优惠券 -¥X」与「实付」预估值。
 *
 * 口径镜像 marketing-service 纯领域服务 `CouponPricing.calculate`（契约 §4.10.1 的服务端落地口径）：
 *   1. 有门槛且订单金额未达 → `0`
 *   2. FIXED / CASH_COUPON → 面值（分）
 *   3. PERCENTAGE → `orderAmount × (100 − value) ÷ 100`（整数除法向下取整）
 *   4. 下限 `0`、封顶订单金额
 *
 * ⚠️ 真值由**后端**在下单时计算（trade `PricingService.applyCoupons` → marketing `useCoupon`）；
 *    本函数只服务于结算页预览，**不参与下单**，且不含促销 / 会员折扣 → 与最终 `payAmount` 可能不同。
 * ⚠️ 未知类型 / 券面缺失 → `0`（不猜）。
 */
export function estimateCouponDiscountCents(input: CouponFaceInput, orderAmountCents: number): number {
  const { discountType, value, minAmount } = input;
  if (orderAmountCents <= 0 || value == null) return 0;
  if (minAmount != null && minAmount > 0 && orderAmountCents < minAmount) return 0;

  let discount: number;
  if (discountType === COUPON_DISCOUNT_TYPE_FIXED || discountType === COUPON_DISCOUNT_TYPE_CASH_COUPON) {
    discount = value;
  } else if (discountType === COUPON_DISCOUNT_TYPE_PERCENTAGE) {
    // 整数分口径：先乘后除、向下取整（与后端 int 除法同解，不引入浮点金额）
    discount = Math.floor((orderAmountCents * (PERCENT_BASE - value)) / PERCENT_BASE);
  } else {
    return 0;
  }
  return Math.max(0, Math.min(discount, orderAmountCents));
}
