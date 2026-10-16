'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient, type ProductSearchVO, type PageResult } from '@icedmall/api';
import { queryKeys } from '../queries';

export function useSearch(keyword: string, categoryId?: number, page = 1, size = 20) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (keyword) params.set('keyword', keyword);
  // `SearchPageReq` 经 **`@QueryModel`** 绑定 → Jackson（全局 SNAKE_CASE）→ 线上名 snake_case
  // （契约 §SE1：`category_id?`；同 `brand?` / `min_price?` / `max_price?`）。
  // 曾发送 camelCase `categoryId` → 被后端静默忽略、品类筛选失效（2026-10-15 修复）。
  if (categoryId) params.set('category_id', String(categoryId));

  return useQuery<PageResult<ProductSearchVO>>({
    queryKey: [...queryKeys.products.lists(), 'search', keyword, categoryId, page],
    queryFn: () => apiClient<PageResult<ProductSearchVO>>(`/api/search/product?${params.toString()}`),
    staleTime: 15 * 1000,
    enabled: true,
  });
}

export function useHotKeywords(limit = 10) {
  return useQuery<string[]>({
    queryKey: ['search', 'hot', limit],
    queryFn: () => apiClient<string[]>(`/api/search/hot?limit=${limit}`),
    staleTime: 5 * 60 * 1000,
  });
}

export function useSearchHistory(limit = 10) {
  return useQuery<string[]>({
    queryKey: ['search', 'history', limit],
    queryFn: () => apiClient<string[]>(`/api/search/history?limit=${limit}`),
    staleTime: 30 * 1000,
  });
}
