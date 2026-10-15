'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type PageResult, type ReviewVO, type ReviewCreateReq } from '@icedmall/api';

/**
 * 评价筛选项。
 *
 * ⚠️ 本组 query 参数由后端 `ReviewFilterReq` **POJO 属性绑定**（`@GetMapping` 形参不是
 * `@RequestParam`），因此**不属**「`@RequestParam(name=…)` 统一为 snake_case」的收敛范围：
 * `hasMedia` 目前仍是 camelCase（契约 §P14 写作 `has_media?`）。
 * 保留现名以免与后端失配，已在交接摘要「待确认项」报出。
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
