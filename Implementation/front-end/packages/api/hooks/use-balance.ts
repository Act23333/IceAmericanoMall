'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@icedmall/api';
import { queryKeys } from '../queries';

/** 查询账户余额（分）—— GET /user/balance */
export function useBalance() {
  return useQuery<number>({
    queryKey: queryKeys.balance.all,
    queryFn: () => apiClient<number>('/api/user/balance'),
  });
}

/** 简易充值 —— POST /user/balance/recharge?amount=（分），返回最新余额。 */
export function useRecharge() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (amount: number) =>
      apiClient<number>(`/api/user/balance/recharge?amount=${amount}`, { method: 'POST' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.balance.all });
      qc.invalidateQueries({ queryKey: queryKeys.user.all });
    },
  });
}
