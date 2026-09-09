'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type FlashSaleEntity } from '@icedmall/api';

export function useActiveFlashSales() {
  return useQuery<FlashSaleEntity[]>({
    queryKey: ['flash-sales', 'active'],
    queryFn: () => apiClient<FlashSaleEntity[]>('/api/item/product/page?sort=sales&order=desc&size=6'),
    // Flash sale 用秒杀专用 endpoint；若后端未单独提供，降级为热门商品
    staleTime: 30 * 1000,
  });
}

export function useFlashSaleBuy() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (flashId: number) =>
      apiClient<void>(`/api/marketing/flash-sale/${flashId}/buy`, { method: 'POST' }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['flash-sales'] }); },
  });
}
