'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@icedmall/api';

interface AfterSaleVO {
  id: number; orderNo: string; userId: number; type: number; reason: string;
  status: number; adminRemark?: string; refundAmount?: number; logisticsNumber?: string; createTime: string;
}

export function useMyAfterSales(page = 1, size = 10) {
  return useQuery<{ records: AfterSaleVO[]; total: number }>({
    queryKey: ['after-sales', page],
    queryFn: () => apiClient<{ records: AfterSaleVO[]; total: number }>(`/api/after-sale?page=${page}&size=${size}`),
  });
}

export function useCreateAfterSale() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: { orderNo: string; type: number; reason: string; refundAmount?: number; logisticsNumber?: string }) =>
      apiClient<AfterSaleVO>('/api/after-sale', { method: 'POST', body: JSON.stringify(req) }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['after-sales'] }); },
  });
}
