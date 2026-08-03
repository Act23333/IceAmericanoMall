'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type FlashSaleEntity } from '@icedmall/api';

/** 进行中的秒杀活动列表 */
export function useFlashSales() {
  return useQuery<FlashSaleEntity[]>({
    queryKey: ['flash-sales', 'active'],
    queryFn: () => apiClient<FlashSaleEntity[]>('/api/flash'),
    staleTime: 10 * 1000, // 秒杀高频刷新
  });
}

/** 秒杀抢购 */
export function useFlashBuy() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (params: { flashId: number; addressId: number }) =>
      apiClient<any>(
        `/api/flash/buy?flashId=${params.flashId}&addressId=${params.addressId}`,
        { method: 'POST' }
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['flash-sales'] });
      qc.invalidateQueries({ queryKey: ['orders'] });
    },
  });
}
