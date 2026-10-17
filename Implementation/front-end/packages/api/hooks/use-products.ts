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
 * 分页 SPU 列表 —— P1 `GET /api/item/spu/page`
 *
 * staleTime: 30s — 商品数据需要相对新鲜
 */
export function useProducts(filters: ProductFilters = {}) {
  return useQuery<PageResult<ProductVO>>({
    queryKey: queryKeys.products.list(filters as Record<string, unknown>),
    queryFn: async () => {
      const params = new URLSearchParams();
      // 后端 `/api/item/spu/page` 以 `SpuPageReq` 经 **`@QueryModel`** 绑定 —— 该形参走
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
      return apiClient<PageResult<ProductVO>>(`/api/item/spu/page?${params.toString()}`);
    },
    staleTime: 30 * 1000,
    gcTime: 5 * 60 * 1000,
  });
}

/**
 * 单个 SPU 详情 —— P2 `GET /api/item/spu/{spuId}`
 *
 * ⚠️ **形参是 SPU 业务键 32-hex `String`**（`SpuVO.spu_id`，契约 §P2 / §1.8）。
 * 2026-10-18 B8 之前该路径变量是**技术键 `Long`**（契约 §十二 12.2 R1 未收口），
 * 形参因此刻意叫 `productId` 以示「不同型、不可互传」；现在**路径变量已改为业务键**
 * （后端 `SpuController#requireSpuId` → `IdGenerator.requireWire` 形态校验，非法即 400），
 * 故形参随契约改名为 `spuId` —— 技术键 `SpuVO.id` 与它**不再有任何寻址关系**。
 *
 * 失败语义：不存在 / 已删除 → **HTTP 404 `NO_RESOURCE_FOUND(1103)`**
 * （客户端 `apiClient` 抛 `ApiError(code=1103)`；`isNotFound()` 判定）。
 *
 * staleTime: 2分钟
 */
export function useProduct(spuId: string) {
  return useQuery<ProductVO>({
    queryKey: queryKeys.products.detail(spuId),
    queryFn: () => apiClient<ProductVO>(`/api/item/spu/${spuId}`),
    staleTime: 2 * 60 * 1000,
    gcTime: 10 * 60 * 1000,
    enabled: !!spuId,
  });
}
