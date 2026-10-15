'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type PageResult, type AfterSaleVO, type AfterSaleApplyReq } from '@icedmall/api';

/**
 * 我的售后列表 —— `GET /api/after-sale?page=&size=`。
 *
 * `page` / `size` 是全小写单词，snake_case 与 camelCase 同形，**不涉及**
 * camelCase→snake_case 收敛，勿改。
 */
export function useMyAfterSales(page = 1, size = 10) {
  return useQuery<PageResult<AfterSaleVO>>({
    queryKey: ['after-sales', page],
    queryFn: () => apiClient<PageResult<AfterSaleVO>>(`/api/after-sale?page=${page}&size=${size}`),
  });
}

/** 申请售后 —— `POST /api/after-sale`，请求体字段 `order_no` / `refund_amount` / `logistics_number`。 */
export function useCreateAfterSale() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: AfterSaleApplyReq) =>
      apiClient<AfterSaleVO>('/api/after-sale', { method: 'POST', body: JSON.stringify(req) }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['after-sales'] }); },
  });
}
