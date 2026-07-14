'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type CouponEntity, type UserCouponEntity } from '@icedmall/api';

export function useAvailableCoupons(_userId?: number) {
  return useQuery<UserCouponEntity[]>({
    queryKey: ['coupons', 'available'],
    queryFn: () => apiClient<UserCouponEntity[]>('/api/coupon/available'),
  });
}

export function useUsedCoupons() {
  return useQuery<UserCouponEntity[]>({
    queryKey: ['coupons', 'used'],
    queryFn: () => apiClient<UserCouponEntity[]>('/api/coupon/used'),
  });
}

export function useCouponTemplates() {
  return useQuery<CouponEntity[]>({
    queryKey: ['coupons', 'templates'],
    queryFn: () => apiClient<CouponEntity[]>('/api/coupon/template'),
    staleTime: 5 * 60 * 1000,
  });
}

export function useClaimCoupon() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (couponId: string) =>
      apiClient<UserCouponEntity>(`/api/coupon/claim?couponId=${couponId}`, { method: 'POST' }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['coupons'] }); },
  });
}
