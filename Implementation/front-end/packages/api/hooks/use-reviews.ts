'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type PageResult, type ReviewVO, type ReviewCreateReq } from '@icedmall/api';

/**
 * SPU 评价列表（筛选版）—— `GET /api/item/review/spu/{spuId}/filter`。
 *
 * ⚠️ **路径变量口径（2026-10-18 B8 已收口）**：`{spuId}` 现为 **SPU 业务键 32-hex `String`**
 * （`ReviewController` 为 `@PathVariable String spuId` + `IdGenerator.requireWire` 形态校验，非法即 400），
 * 与 `ReviewVO.spu_id` **同型同义** —— 旧实现（资源段 `review/product/`、变量为技术键 `Long`）
 * 已随 ADR-062 §决策 2/3 一并废止。故形参名从 `spuTechnicalId` 改为 `spuId`。
 *
 * ⚠️ 筛选 query 参数由后端 `ReviewFilterReq` 经 **`@QueryModel`** 绑定 —— 即走 Jackson
 * 反序列化（全局 `property-naming-strategy: SNAKE_CASE`），**线上名一律 snake_case**：
 * `rating` / `has_media` / `sort` / `page` / `size`（契约 §P14）。
 * 曾误判为「裸 POJO 属性绑定、不受命名策略管辖」而发送 camelCase `hasMedia` →
 * 后端按未知属性静默忽略、「只看带图」筛选永不生效且不报错（2026-10-15 修复）。
 */
/**
 * 评价列表排序方式 —— **逐值镜像后端** `item-service/domain/enums/ReviewSortEnum.java`
 * （`NEWEST("newest")` / `HIGHEST("highest")` / `LOWEST("lowest")`）。
 *
 * ⚠️ 曾经的线上缺陷：页面写了 `sort=oldest` —— 后端**没有这个取值**。更糟的是它不是
 * 报错而是**静默**：`ReviewSortEnum.fromCode()` 对未知码回退 `NEWEST` ⇒ 用户点「最早」
 * 得到的是「最新」，且无任何提示。故此处用**字面量联合类型**把非法值变成编译期错误，
 * 并在发送前再做一次运行期白名单校验（防未经类型的 JS 调用方）。
 *
 * 「按时间正序（最旧优先）」属**后端能力缺口**（`ReviewSortEnum` 无该取值、
 * `ReviewFilterReq.sort` 注释也仅列 newest/highest/lowest），前端**不得**私自定义编码。
 */
export const REVIEW_SORT = {
  NEWEST: 'newest',
  HIGHEST: 'highest',
  LOWEST: 'lowest',
} as const;

/** 合法的排序码联合类型（`'newest' | 'highest' | 'lowest'`）。 */
export type ReviewSort = (typeof REVIEW_SORT)[keyof typeof REVIEW_SORT];

/** 合法排序码清单（运行期白名单）。 */
export const REVIEW_SORT_VALUES: readonly ReviewSort[] = Object.values(REVIEW_SORT);

export interface ReviewFilter {
  rating?: number;
  hasMedia?: boolean;
  /** 排序码；只接受 `ReviewSort`（`oldest` 之类的非法码在编译期即被拒）。 */
  sort?: ReviewSort;
}

/**
 * SPU 评价列表（筛选版）。
 *
 * @param spuId **SPU 业务键 32-hex**（`SpuVO.spu_id` / `ReviewVO.spu_id` 同口径）。
 *   缺省时**不发请求**（`enabled`）—— 形态非法的路径变量会换来 400，不是空列表。
 */
export function useProductReviews(spuId: string, filter: ReviewFilter = {}, page = 1, size = 10) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (filter.rating) params.set('rating', String(filter.rating));
  if (filter.hasMedia) params.set('has_media', 'true');
  // 运行期白名单：非法排序码**不发送**（宁可用后端默认 newest，也不发一个后端会静默回退的码）
  if (filter.sort && REVIEW_SORT_VALUES.includes(filter.sort)) params.set('sort', filter.sort);

  return useQuery<PageResult<ReviewVO>>({
    queryKey: ['reviews', spuId, filter, page],
    queryFn: () => apiClient<PageResult<ReviewVO>>(
      `/api/item/review/spu/${spuId}/filter?${params.toString()}`
    ),
    staleTime: 60 * 1000,
    enabled: !!spuId,
  });
}

/**
 * 发表评价 —— `POST /api/item/review`。
 *
 * ⚠️ **B3 改名**：请求体键为 **`spu_id`**（不是 `product_id`），且 `spu_id` / `sku_id`
 * 均为 **32-hex `String`** 业务键（B2.5 收口）。旧代码发 `product_id` → 后端
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
