'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type PageResult, type ReviewVO, type ReviewCreateReq } from '@icedmall/api';

/**
 * 商品评价列表（筛选版）—— `GET /api/item/review/product/{spuId}/filter`。
 *
 * ⚠️ **路径变量口径（契约 §十二 12.2 R1，未收口）**：`{spuId}` 仍是**技术键 `Long`**
 * （`ReviewController` 为 `@PathVariable Long spuId`），**不是** `ReviewVO.spu_id` 的
 * 32-hex 业务键。二者不同型、不可互传 —— 故形参命名为 `spuTechnicalId` 以免误用。
 *
 * ⚠️ 筛选 query 参数由后端 `ReviewFilterReq` 经 **`@QueryModel`** 绑定 —— 即走 Jackson
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

/** 商品评价列表（筛选版）。`spuTechnicalId` = `GET /api/item/product/{id}` 的同款技术键。 */
export function useProductReviews(spuTechnicalId: number, filter: ReviewFilter = {}, page = 1, size = 10) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (filter.rating) params.set('rating', String(filter.rating));
  if (filter.hasMedia) params.set('has_media', 'true');
  if (filter.sort) params.set('sort', filter.sort);

  return useQuery<PageResult<ReviewVO>>({
    queryKey: ['reviews', spuTechnicalId, filter, page],
    queryFn: () => apiClient<PageResult<ReviewVO>>(
      `/api/item/review/product/${spuTechnicalId}/filter?${params.toString()}`
    ),
    staleTime: 60 * 1000,
  });
}

/**
 * 发表评价 —— `POST /api/item/review`。
 *
 * ⚠️ **B3 改名**：请求体键为 **`spu_id`**（不是 `product_id`），且 `spu_id` / `sku_id`
 * 均为 **32-hex `String` 业务键**（B2.5 收口）。旧代码发 `product_id` → 后端
 * `ReviewCreateReq.spuId` 收不到值（`@NotNull`）⇒ 400；且类型若传数字亦不合口径。
 */
export function useCreateReview() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ReviewCreateReq) =>
      apiClient<ReviewVO>('/api/item/review', { method: 'POST', body: JSON.stringify(req) }),
    onSuccess: (_data, vars) => { qc.invalidateQueries({ queryKey: ['reviews', vars.spu_id] }); },
  });
}
