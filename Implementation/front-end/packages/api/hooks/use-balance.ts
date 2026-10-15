'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@icedmall/api';
import { queryKeys } from '../queries';

/**
 * 查询账户余额（分）。
 *
 * ✅ V5.3 边界重构后余额域归属 wallet-service：`GET /api/wallet`
 *    （原 user-service `/api/user/balance` 已下线，契约 §4.2.4）
 */
export function useBalance() {
  return useQuery<number>({
    queryKey: queryKeys.balance.all,
    queryFn: () => apiClient<number>('/api/wallet'),
  });
}

/**
 * 简易充值（分）—— `POST /api/wallet/recharge?amount=`，返回最新余额。
 *
 * 注：`amount` 为 query / 表单参数（**不是** JSON body），Jackson 命名策略不影响它；
 * 后端已收紧缺失或 ≤0 → 1101 PARAM_ERROR（契约 §4.2.10 W2）。
 */
export function useRecharge() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (amount: number) =>
      apiClient<number>(`/api/wallet/recharge?amount=${amount}`, { method: 'POST' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.balance.all });
      qc.invalidateQueries({ queryKey: queryKeys.user.all });
    },
  });
}
