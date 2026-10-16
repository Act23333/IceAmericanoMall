'use client';

import { useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type CouponVO, type UserCouponVO } from '@icedmall/api';

/** 我的未使用券 —— `GET /api/coupon/available`。 */
export function useAvailableCoupons(_userId?: number) {
  return useQuery<UserCouponVO[]>({
    queryKey: ['coupons', 'available'],
    queryFn: () => apiClient<UserCouponVO[]>('/api/coupon/available'),
  });
}

/** 我的已使用/已过期券 —— `GET /api/coupon/used`。 */
export function useUsedCoupons() {
  return useQuery<UserCouponVO[]>({
    queryKey: ['coupons', 'used'],
    queryFn: () => apiClient<UserCouponVO[]>('/api/coupon/used'),
  });
}

/** 可领取券模板 —— `GET /api/coupon/template`。 */
export function useCouponTemplates() {
  return useQuery<CouponVO[]>({
    queryKey: ['coupons', 'templates'],
    queryFn: () => apiClient<CouponVO[]>('/api/coupon/template'),
    staleTime: 5 * 60 * 1000,
  });
}

/**
 * 智能领取 —— `POST /api/coupon/claim?coupon_id=`。
 * 注：`coupon_id` 是 **query 参数**（由 `@RequestParam(name = "coupon_id")` 决定）——
 * 平台决策：线上查询参数一律 snake_case，勿改回 camelCase。
 * 局部形参 `couponId` 是前端内部标识符，保持 camelCase。
 */
export function useClaimCoupon() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (couponId: string) =>
      apiClient<UserCouponVO>(`/api/coupon/claim?coupon_id=${encodeURIComponent(couponId)}`, { method: 'POST' }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['coupons'] }); },
  });
}

/** 秒杀级领取 —— `POST /api/coupon/grab?coupon_id=`。 */
export function useGrabCoupon() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (couponId: string) =>
      apiClient<UserCouponVO>(`/api/coupon/grab?coupon_id=${encodeURIComponent(couponId)}`, { method: 'POST' }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['coupons'] }); },
  });
}

/* ================================================================
   结算页可用券 —— 单请求（券面随 UserCouponVO 一并返回）
   ================================================================
   后端 `CouponController` 只有 claim / grab / available / used / template / count，
   **不存在** `POST /api/coupon/available/filter`（原前端该调用指向不存在的端点）。

   现口径：**只打 `GET /api/coupon/available`（M3）**，券面 `name` / `value` / `min_amount` /
   `discount_type` 已由后端应用层读模型回填在同一响应里（ADR-057，fail-open → null），
   故**不再**额外请求 `GET /api/coupon/template` 做前端本地 join。

   面额口径：`value` 的单位随 `discount_type` 变化（1/3=分，2=百分比），本 hook 只做**透传**，
   不在视图模型里做任何类型归一（归一由 `@icedmall/utils` 的 `formatCouponFace()` 承担）。

   保留的空值兜底：券面字段为 null（模板已删除 / 回填降级）时仍展示该券行，
   名称回空串、面额原样 null，由结算页经 `formatCouponFace()` 渲染「优惠券」降级文案 ——
   不因展示字段缺失而丢券。
   ================================================================ */

/** 结算页可选用券（前端本地视图模型 —— 内部标识符保持 camelCase）。 */
export interface CheckoutCoupon {
  /** 下单请求体 `CreateOrderReq.user_coupon_id` 取此值（UserCouponVO.id）。 */
  userCouponId: number;
  /** 券业务券号（32-hex）；后端 fail-open 时为 null（不影响下单，下单用 userCouponId）。 */
  couponId: string | null;
  name: string;
  /**
   * 折扣类型（契约 §4.10.1）：`1`-满减 / `2`-折扣 / `3`-代金券；`null` = 未知（降级）。
   * 面额渲染必须传 `@icedmall/utils` 的 `formatCouponFace()`，**不得**再假定满减口径。
   */
  discountType: number | null;
  /** 券面值：满减/代金券为**分**，折扣为**百分比**；券面缺失为 null（**不回落为 0**）。 */
  value: number | null;
  /** 使用门槛（分）；券面缺失时回落 0（= 无门槛，仅用于本地预过滤）。 */
  minAmount: number;
  /** 适用范围类型（`null` = 降级，按 ALL 处理）。仅用于展示与诊断，不参与安全判定。 */
  scopeType: number | null;
  /** 适用范围值（CSV 串）。仅用于展示与诊断。 */
  scopeValues: string | null;
}

/* ================================================================
   券适用范围 · 本地预过滤（契约 §4.10.1 / §十二 12.2【④ 券范围投影】）

   ⚠️ **这只是体验优化，不是安全边界。**
      核销时服务端 `CouponScope.accepts` 仍做**权威校验**（R3 已随 B2 收口为业务键同型同义），
      本端预过滤多滤/漏滤都**不改变**该券能否真正使用 —— 结算页提交后以服务端结果为准。
      故本函数一律**宁可放行、不可错滤**（fail-open）。

   ⚠️ `scope_type` 为 `null` → **按 ALL 放行**（硬规则）。
      后端 fail-open 口径：券模板已删除 / 读模型降级 → `scope_type` 与 `scope_values` **同为 null**
      （不回落为 1、不回落为空串）。降级态恰恰是最需要容错的时候，把 `null` 当「不适用」滤掉
      会把可用券藏起来。用 `== null` 判定可同时覆盖 `null` 与旧报文缺失键（`undefined`）。
   ================================================================ */

/** `scope_type=1`：全场通用。 */
export const COUPON_SCOPE_ALL = 1;
/** `scope_type=2`：指定品类。 */
export const COUPON_SCOPE_CATEGORY = 2;
/** `scope_type=3`：指定商品（`scope_values` 为 **SPU 业务键 32-hex** CSV）。 */
export const COUPON_SCOPE_PRODUCT = 3;

/** CSV 串 → 去空去重的值数组（`scope_values` 对外口径为 CSV）。 */
export function parseScopeValues(scopeValues: string | null | undefined): string[] {
  if (!scopeValues) return [];
  return scopeValues
    .split(',')
    .map((v) => v.trim())
    .filter((v) => v !== '');
}

/**
 * 券是否**可能**适用于本单（本地预过滤，fail-open）。
 *
 * @param scopeType   报文 `scope_type`（`null` = 降级 → 视为 ALL）
 * @param scopeValues 报文 `scope_values`（CSV）
 * @param cartSpuIds  当前购物车涉及的商品 **SPU 业务键**（32-hex）
 */
export function passesCouponScope(
  scopeType: number | null | undefined,
  scopeValues: string | null | undefined,
  cartSpuIds: string[],
): boolean {
  // ① 降级态 → 按 ALL 放行（不得错滤）
  if (scopeType == null) return true;
  // ② 全场通用
  if (scopeType === COUPON_SCOPE_ALL) return true;

  const values = parseScopeValues(scopeValues);
  // ③ 范围值缺失/为空 → 无法判定 → 放行（服务端仍会权威校验）
  if (values.length === 0) return true;

  // ④ 指定商品：与购物车 SPU 业务键求交（R3 已收口 ⇒ 两侧同型同义，可命中）
  if (scopeType === COUPON_SCOPE_PRODUCT) {
    if (cartSpuIds.length === 0) return true; // 拿不到购物车商品键 → 不预滤
    return cartSpuIds.some((id) => values.includes(id));
  }

  // ⑤ 指定品类：`CartItemVO` **不含** `category_id`（B2 未投影），本端**无法求值**
  //    → 一律放行（fail-open）。要真正预过滤需后端在购物车读模型里补 `category_id`。
  if (scopeType === COUPON_SCOPE_CATEGORY) return true;

  // ⑥ 未知取值 → 放行
  return true;
}

/**
 * 结算页可用券预过滤（仅 `GET /api/coupon/available`，无第二次请求、无本地 join）。
 *
 * @param totalAmount 当前结算商品总额（分）
 * @param cartSpuIds  当前购物车商品的 **SPU 业务键**（用于 `scope_type=3` 预过滤；缺省不预滤）
 */
export function useCheckoutCoupons(totalAmount: number, cartSpuIds: string[] = []) {
  const available = useAvailableCoupons();
  // 数组字面量每渲染都变 → 用内容签名字符串做 memo 依赖，避免无谓重算
  const spuKey = cartSpuIds.join(',');

  const data = useMemo<CheckoutCoupon[]>(() => {
    const couponRows = available.data ?? [];
    if (couponRows.length === 0) return [];
    const spuIds = spuKey ? spuKey.split(',') : [];

    return couponRows
      // 后端 `UserCouponStatusEnum`：1=未使用 2=已使用 3=已过期（`/available` 只返未使用，此处仍显式守卫）
      .filter((uc) => uc.status === 1)
      // 券适用范围预过滤（体验优化，fail-open；服务端核销仍权威校验）
      .filter((uc) => passesCouponScope(uc.scope_type, uc.scope_values, spuIds))
      .map((uc) => {
        // 券面缺失（模板已删除 / 回填降级）时为 null → 名称为空串、面额原样透传 null，
        // 由消费端（结算页）经 `formatCouponFace()` 渲染「优惠券」降级文案 ——
        // ⚠️ 不回落为 0：0 是「减 0 元」的合法面额，会把「未知」伪装成「满减 0 元」。
        return {
          userCouponId: uc.id,
          couponId: uc.coupon_id,
          name: uc.name ?? '',
          // 折扣类型唯一口径 `discount_type`（`coupon.type` 自 V4.0 DEPRECATED，后端不投影）
          discountType: uc.discount_type,
          value: uc.value,
          minAmount: uc.min_amount ?? 0,
          scopeType: uc.scope_type ?? null,
          scopeValues: uc.scope_values ?? null,
        };
      })
      .filter((c) => c.minAmount <= totalAmount);
  }, [available.data, totalAmount, spuKey]);

  return {
    data,
    isLoading: available.isLoading,
    error: available.error,
  };
}
