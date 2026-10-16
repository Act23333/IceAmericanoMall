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
      // 后端 `/api/item/product/page` 以 `SpuPageReq` 经 **`@QueryModel`** 绑定 —— 该形参走
      // Jackson（全局 `property-naming-strategy: SNAKE_CASE`），**线上名一律 snake_case**
      // （契约 §P1：`category_id?` / `seller_id?`）。曾误判为「裸 POJO 属性绑定、不受命名策略
      // 管辖」而发送 camelCase `categoryId` → 后端按未知属性静默忽略、`categoryId` 恒 null，
      // 品类筛选整体失效且不报错（2026-10-15 修复）。本组参数一律 snake_case。
      if (filters.categoryId) params.set('category_id', String(filters.categoryId));
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
 * ⚠️ 形参是 `GET /api/item/product/{id}` 的**路径变量** —— 契约 §十二 12.2 R1 未收口，
 * 它仍是**技术键 `Long`**（路由 `/product/{id}` 取 `SpuVO.id`），**不是** `SpuVO.spu_id`
 * 的 32-hex 业务键。二者不同型、不可互传，故形参名保持 `productId`（技术键语义）而不叫 `spuId`。
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
