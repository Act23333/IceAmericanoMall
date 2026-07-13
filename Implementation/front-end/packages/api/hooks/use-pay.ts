'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient, type PayOrderVO, type PayChannel } from '@icedmall/api';
import { queryKeys } from '../queries';

/**
 * 发起支付 —— POST /api/pay/order/{orderNo}?channel=WECHAT|ALIPAY|BALANCE
 * 余额支付即时成功（status=3，无二维码）；微信/支付宝返回支付链接（qrCodeUrl）。
 */
export function useInitiatePay() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ orderNo, channel }: { orderNo: string; channel: PayChannel }) =>
      apiClient<PayOrderVO>(`/api/pay/order/${orderNo}?channel=${channel}`, { method: 'POST' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.orders.all });
      qc.invalidateQueries({ queryKey: queryKeys.balance.all });
    },
  });
}

/** 查询支付单状态 —— GET /api/pay/order/{payOrderNo}/status（可轮询）。 */
export function usePayStatus(payOrderNo: string, options?: { refetchInterval?: number; enabled?: boolean }) {
  return useQuery<PayOrderVO>({
    queryKey: queryKeys.pay.status(payOrderNo),
    queryFn: () => apiClient<PayOrderVO>(`/api/pay/order/${payOrderNo}/status`),
    enabled: (options?.enabled ?? true) && !!payOrderNo,
    refetchInterval: options?.refetchInterval,
  });
}
