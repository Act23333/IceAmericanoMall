'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../client';
import type { CategoryVO } from '../types';
import { queryKeys } from '../queries';

/**
 * 获取分类树 (展示在商城导航和分类入口)
 *
 * staleTime: 5分钟 — 类目结构变化极少
 */
export function useCategories() {
  return useQuery<CategoryVO[]>({
    queryKey: queryKeys.categories.tree(),
    queryFn: () => apiClient<CategoryVO[]>('/api/item/category/tree'),
    staleTime: 5 * 60 * 1000,
    gcTime: 10 * 60 * 1000,
  });
}
