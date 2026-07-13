'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../client';
import type { ProductVO, PageResult } from '../types';
import { queryKeys } from '../queries';

interface ProductFilters {
  categoryId?: number;
  keyword?: string;
  sort?: 'sales' | 'price' | 'newest';
  order?: 'asc' | 'desc';
  page?: number;
  size?: number;
}

/**
 * 分页商品列表
 *
 * staleTime: 30s — 商品数据需要相对新鲜
 */
export function useProducts(filters: ProductFilters = {}) {
  return useQuery<PageResult<ProductVO>>({
    queryKey: queryKeys.products.list(filters as Record<string, unknown>),
    queryFn: async () => {
      const params = new URLSearchParams();
      if (filters.categoryId) params.set('categoryId', String(filters.categoryId));
      if (filters.keyword) params.set('keyword', filters.keyword);
      if (filters.sort) params.set('sort', filters.sort);
      if (filters.order) params.set('order', filters.order);
      if (filters.page) params.set('page', String(filters.page));
      if (filters.size) params.set('size', String(filters.size));
      return apiClient<PageResult<ProductVO>>(`/api/item/product/page?${params.toString()}`);
    },
    staleTime: 30 * 1000,
    gcTime: 5 * 60 * 1000,
  });
}

/**
 * 单个商品详情
 *
 * staleTime: 2分钟
 */
export function useProduct(productId: string) {
  return useQuery<ProductVO>({
    queryKey: queryKeys.products.detail(productId),
    queryFn: () => apiClient<ProductVO>(`/api/item/product/${productId}`),
    staleTime: 2 * 60 * 1000,
    gcTime: 10 * 60 * 1000,
    enabled: !!productId,
  });
}
