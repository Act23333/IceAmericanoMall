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

export function useAddToCart() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CartAddReq) => apiClient<void>('/api/cart/item', { method: 'POST', body: JSON.stringify(req) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.cart.mine() }),
  });
}

export function useUpdateCartItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CartUpdateReq) => apiClient<void>('/api/cart/item', { method: 'PUT', body: JSON.stringify(req) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.cart.mine() }),
  });
}

export function useRemoveCartItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (skuId: number) => apiClient<void>(`/api/cart/item/${skuId}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.cart.mine() }),
  });
}

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
