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

   现口径：**只打 `GET /api/coupon/available`（M3）**，券面 `name` / `value` / `min_amount`
   已由后端应用层读模型回填在同一响应里（ADR-057，fail-open → null），
   故**不再**额外请求 `GET /api/coupon/template` 做前端本地 join。

   保留的空值兜底：券面三字段为 null（模板已删除 / 回填降级）时仍展示该券行，
   名称回空串，由结算页渲染「满 X 减 Y」式占位文案 —— 不因展示字段缺失而丢券。
   ================================================================ */

/** 结算页可选用券（前端本地视图模型 —— 内部标识符保持 camelCase）。 */
export interface CheckoutCoupon {
  /** 下单请求体 `CreateOrderReq.user_coupon_id` 取此值（UserCouponVO.id）。 */
  userCouponId: number;
  /** 券业务券号（32-hex）；后端 fail-open 时为 null（不影响下单，下单用 userCouponId）。 */
  couponId: string | null;
  name: string;
  /** 1-满减 2-折扣。 */
  type: number;
  /** 面额（分）或折扣值。 */
  value: number;
  /** 使用门槛（分）。 */
  minAmount: number;
}

/**
 * 结算页可用券预过滤（仅 `GET /api/coupon/available`，无第二次请求、无本地 join）。
 *
 * @param totalAmount 当前结算商品总额（分）
 */
export function useCheckoutCoupons(totalAmount: number) {
  const available = useAvailableCoupons();

  const data = useMemo<CheckoutCoupon[]>(() => {
    const couponRows = available.data ?? [];
    if (couponRows.length === 0) return [];

    return couponRows
      // 后端 `UserCouponStatusEnum`：1=未使用 2=已使用 3=已过期（`/available` 只返未使用，此处仍显式守卫）
      .filter((uc) => uc.status === 1)
      .map((uc) => {
        // 券面缺失（模板已删除 / 回填降级）时为 null → 名称为空串，由消费端（结算页）渲染占位文案
        return {
          userCouponId: uc.id,
          couponId: uc.coupon_id,
          name: uc.name ?? '',
          // `type` 未随 `UserCouponVO` 返回（后端未投影）→ 结算页只按面额展示，恒按满减口径
          type: 1,
          value: uc.value ?? 0,
          minAmount: uc.min_amount ?? 0,
        };
      })
      .filter((c) => c.minAmount <= totalAmount);
  }, [available.data, totalAmount]);

  return {
    data,
    isLoading: available.isLoading,
    error: available.error,
  };
}
