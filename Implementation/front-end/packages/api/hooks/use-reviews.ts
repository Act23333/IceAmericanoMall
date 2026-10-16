'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type PageResult, type ReviewVO, type ReviewCreateReq } from '@icedmall/api';

/**
 * 评价筛选项。
 *
 * ⚠️ 本组 query 参数由后端 `ReviewFilterReq` 经 **`@QueryModel`** 绑定 —— 即走 Jackson
 * 反序列化（全局 `property-naming-strategy: SNAKE_CASE`），**线上名一律 snake_case**：
 * `rating` / `has_media` / `sort` / `page` / `size`（契约 §P14）。
 * 曾误判为「裸 POJO 属性绑定、不受命名策略管辖」而发送 camelCase `hasMedia` →
 * 后端按未知属性静默忽略、「只看带图」筛选永不生效且不报错（2026-10-15 修复）。
 */
export interface ReviewFilter {
  rating?: number;
  hasMedia?: boolean;
  sort?: string;
}

/** 商品评价列表（筛选版）—— `GET /api/item/review/product/{productId}/filter`。 */
export function useProductReviews(productId: number, filter: ReviewFilter = {}, page = 1, size = 10) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (filter.rating) params.set('rating', String(filter.rating));
  if (filter.hasMedia) params.set('has_media', 'true');
  if (filter.sort) params.set('sort', filter.sort);

  return useQuery<PageResult<ReviewVO>>({
    queryKey: ['reviews', productId, filter, page],
    queryFn: () => apiClient<PageResult<ReviewVO>>(
      `/api/item/review/product/${productId}/filter?${params.toString()}`
    ),
    staleTime: 60 * 1000,
  });
}

/**
 * 发表评价 —— `POST /api/item/review`。
 *
 * ⚠️ 请求体字段为 `product_id` / `order_no`（订单号字符串，32 位 hex）/ `sku_id`，
 * 不是 `productId` / `orderId` / `skuId`（旧代码的 `orderId` 后端无此字段，会被静默忽略）。
 */
export function useCreateReview() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ReviewCreateReq) =>
      apiClient<ReviewVO>('/api/item/review', { method: 'POST', body: JSON.stringify(req) }),
    onSuccess: (_data, vars) => { qc.invalidateQueries({ queryKey: ['reviews', vars.product_id] }); },
  });
}
