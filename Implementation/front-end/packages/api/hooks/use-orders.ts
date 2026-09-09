'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type OrderVO, type PageResult, type CreateOrderReq } from '@icedmall/api';
import { queryKeys } from '../queries';

export function useOrders(params: { status?: number; page?: number; size?: number }) {
  const { status, page = 1, size = 20 } = params;
  let path = `/api/trade/order/page?page=${page}&size=${size}`;
  if (status !== undefined) path += `&status=${status}`;

  return useQuery<PageResult<OrderVO>>({
    queryKey: queryKeys.orders.list(params),
    queryFn: () => apiClient<PageResult<OrderVO>>(path),
  });
}

export function useOrder(orderNo: string) {
  return useQuery<OrderVO>({
    queryKey: queryKeys.orders.detail(orderNo),
    queryFn: () => apiClient<OrderVO>(`/api/trade/order/${orderNo}`),
    enabled: !!orderNo,
  });
}

export function useCreateOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateOrderReq) =>
      apiClient<OrderVO>('/api/trade/order', { method: 'POST', body: JSON.stringify(req) }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.orders.all });
      qc.invalidateQueries({ queryKey: queryKeys.cart.all });
    },
  });
}

export function useCancelOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (orderNo: string) =>
      apiClient<void>(`/api/trade/order/${orderNo}/cancel`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.orders.all }),
  });
}

export function useConfirmOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (orderNo: string) =>
      apiClient<void>(`/api/trade/order/${orderNo}/confirm`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.orders.all }),
  });
}
