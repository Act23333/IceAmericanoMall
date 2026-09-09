'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type PageResult } from '@icedmall/api';

interface ReviewVO {
  id: number; userId: number; username?: string; avatar?: string;
  productId: number; orderId: number; skuId: number;
  rating: number; content: string; images?: string; tags?: string;
  reply?: string; replyTime?: string;
  appendContent?: string; appendMediaUrls?: string; appendTime?: string;
  likeCount?: number; likedByMe?: boolean;
  createTime: string;
}

interface ReviewFilter {
  rating?: number;
  hasMedia?: boolean;
  sort?: string;
}

export function useProductReviews(productId: number, filter: ReviewFilter = {}, page = 1, size = 10) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (filter.rating) params.set('rating', String(filter.rating));
  if (filter.hasMedia) params.set('hasMedia', 'true');
  if (filter.sort) params.set('sort', filter.sort);

  return useQuery<PageResult<ReviewVO>>({
    queryKey: ['reviews', productId, filter, page],
    queryFn: () => apiClient<PageResult<ReviewVO>>(
      `/api/item/review/product/${productId}/filter?${params.toString()}`
    ),
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
