'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type FlashSaleVO, type FlashBuyVO } from '@icedmall/api';

/** 进行中的秒杀活动列表 —— `GET /api/flash`。 */
export function useFlashSales() {
  return useQuery<FlashSaleVO[]>({
    queryKey: ['flash-sales', 'active'],
    queryFn: () => apiClient<FlashSaleVO[]>('/api/flash'),
    staleTime: 10 * 1000, // 秒杀高频刷新
  });
}

/**
 * 秒杀抢购 —— `POST /api/flash/buy?flash_id=&address_id=`。
 *
 * 查询串 key 为 snake_case（`@RequestParam(name = "flash_id"/"address_id")`，平台决策：
 * 线上字段与查询参数一律 snake_case）；入参对象与 `params.flashId` 是前端内部标识符，
 * 保持 camelCase。
 */
export function useFlashBuy() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (params: { flashId: number; addressId: number }) =>
      apiClient<FlashBuyVO>(
        `/api/flash/buy?flash_id=${params.flashId}&address_id=${params.addressId}`,
        { method: 'POST' }
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['flash-sales'] });
      qc.invalidateQueries({ queryKey: ['orders'] });
    },
  });
}
