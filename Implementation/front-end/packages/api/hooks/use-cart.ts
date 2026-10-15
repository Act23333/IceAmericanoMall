'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type CartVO, type CartAddReq, type CartUpdateReq } from '@icedmall/api';
import { queryKeys } from '../queries';

export function useCart() {
  return useQuery<CartVO>({
    queryKey: queryKeys.cart.mine(),
    queryFn: () => apiClient<CartVO>('/api/cart'),
    staleTime: 10 * 1000,
  });
}

/** 加购 —— 请求体 `CartAddReq` 走全局 SNAKE_CASE，字段为 `sku_id` / `quantity`。 */
export function useAddToCart() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CartAddReq) => apiClient<void>('/api/cart/item', { method: 'POST', body: JSON.stringify(req) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.cart.mine() }),
  });
}

/** 改数量 —— 请求体 `CartUpdateReq`（`sku_id` / `quantity`；quantity ≤ 0 即删除）。 */
export function useUpdateCartItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CartUpdateReq) => apiClient<void>('/api/cart/item', { method: 'PUT', body: JSON.stringify(req) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.cart.mine() }),
  });
}

/** 删除单项 —— path variable `skuId` 由 `@PathVariable Long skuId` 决定，保持 camelCase。 */
export function useRemoveCartItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (skuId: number) => apiClient<void>(`/api/cart/item/${skuId}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.cart.mine() }),
  });
}

/** 单选切换 —— query 参数名为 `selected`（`@RequestParam Boolean selected`）。 */
export function useToggleSelect() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ skuId, selected }: { skuId: number; selected: boolean }) =>
      apiClient<void>(`/api/cart/item/${skuId}/selected?selected=${selected}`, { method: 'PATCH' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.cart.mine() }),
  });
}

export function useClearCart() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => apiClient<void>('/api/cart/clear', { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.cart.mine() }),
  });
}
