'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type PageResult } from '@icedmall/api';

interface ReviewVO {
  id: number; userId: number; productId: number; orderId: number; skuId: number;
  rating: number; content: string; images?: string; createTime: string;
}

export function useProductReviews(productId: number, page = 1, size = 10) {
  return useQuery<PageResult<ReviewVO>>({
    queryKey: ['reviews', productId, page],
    queryFn: () => apiClient<PageResult<ReviewVO>>(`/api/item/review/product/${productId}?page=${page}&size=${size}`),
    staleTime: 60 * 1000,
  });
}

export function useCreateReview() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: { productId: number; orderId: number; skuId: number; rating: number; content: string; images?: string }) =>
      apiClient<ReviewVO>('/api/item/review', { method: 'POST', body: JSON.stringify(req) }),
    onSuccess: (_data, vars) => { qc.invalidateQueries({ queryKey: ['reviews', vars.productId] }); },
  });
}
