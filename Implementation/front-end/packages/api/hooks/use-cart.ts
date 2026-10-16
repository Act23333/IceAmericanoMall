'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type CartVO, type CartAddReq, type CartUpdateReq } from '@icedmall/api';
import { queryKeys } from '../queries';

/* ================================================================
   服务端购物车（cart-service，契约 §4.5.1）
   ================================================================
   标识口径（契约 §1.8② / ADR-061）：C2–C5 的 SKU 标识**全部为 SKU 业务键 32-hex `String`**。

   ⚠️ **路径变量也是业务键**（C4/C5 已随 B2 收口，与 `CartItemVO.sku_id` 同型同义）——
      `/api/cart/item/{skuId}` 里的 `{skuId}` **不再**是 `Long`。
      故本文件所有 `skuId` 形参均为 `string`（前端内部标识符保持 camelCase，
      与线上名 `sku_id` / `{skuId}` 是两回事）。

   ⚠️ **未接线端点（不得对接）**：
      - C8 `PATCH /api/cart/select-all` —— 契约标 ⚪ 未实现；
      - C9 `DELETE /api/cart/batch` —— ⚪ 未实现；
      - C10 `POST /api/cart/item/{skuId}/move-to-favorite` —— **⚪ 未实现**
        （实测后端 `CartController` 零命中该路由）。
      故「全选 / 批量删除 / 移入收藏」没有单请求可用，只能按行扇出（见 `cart-model.ts`）。
   ================================================================ */

/**
 * 购物车视图 —— `GET /api/cart`（C1）。`CartItemVO.sku_id` 为 SKU 业务键 32-hex。
 *
 * ⚠️ **必须传 `enabled`**（`/api/cart` 是 🔒 端点）：未登录时若照发，会拿到 **401**
 * → `client.ts` 走刷新兜底 → 无 `refresh_token` ⇒ `handleRefreshFailure()` ⇒ 广播
 * `auth:logout` ⇒ `providers.tsx` 的登出监听把**匿名访客弹到登录页**。
 * 这正是「未登录可用性」不得破坏的那条线 —— 故由调用方（`cart-model.ts`）按登录态开关。
 */
export function useCart(options: { enabled?: boolean } = {}) {
  return useQuery<CartVO>({
    queryKey: queryKeys.cart.mine(),
    queryFn: () => apiClient<CartVO>('/api/cart'),
    staleTime: 10 * 1000,
    enabled: options.enabled ?? true,
  });
}

/**
 * 加购 —— `POST /api/cart/item`（C2）。
 * 请求体 `CartAddReq` 走全局 SNAKE_CASE，字段为 `sku_id` / `quantity`。
 * 服务端 `(user_id, sku_id)` 唯一 ⇒ 同 SKU 再来一次是**增数量**，不会产生第二行。
 */
export function useAddToCart() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CartAddReq) => apiClient<void>('/api/cart/item', { method: 'POST', body: JSON.stringify(req) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.cart.mine() }),
  });
}

/** 改数量 —— `PUT /api/cart/item`（C3），请求体 `CartUpdateReq`（`sku_id` / `quantity`；quantity ≤ 0 即删除）。 */
export function useUpdateCartItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CartUpdateReq) => apiClient<void>('/api/cart/item', { method: 'PUT', body: JSON.stringify(req) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.cart.mine() }),
  });
}

/** 删除单项 —— `DELETE /api/cart/item/{skuId}`（C4），路径变量为 **SKU 业务键 32-hex**。 */
export function useRemoveCartItem() {
  const qc = useQueryClient();
  return useMutation({
    // 业务键形态为严格 32 位小写 hex（契约 §1.8）⇒ 路径段安全，无需 encodeURIComponent
    // （加编码反而会让 `crosscheck-api-paths.mjs` 的路径提取在 '(' 处截断，污染守卫输出）
    mutationFn: (skuId: string) => apiClient<void>(`/api/cart/item/${skuId}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.cart.mine() }),
  });
}

/**
 * 单选切换 —— `PATCH /api/cart/item/{skuId}/selected?selected=`（C5）。
 * 路径变量为 SKU 业务键（32-hex）；query key 为 `selected`
 * （后端 `@RequestParam Boolean selected` 未显式钉名 ⇒ 线上名 = 形参名 `selected`）。
 */
export function useToggleSelect() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ skuId, selected }: { skuId: string; selected: boolean }) =>
      apiClient<void>(`/api/cart/item/${skuId}/selected?selected=${selected}`, { method: 'PATCH' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.cart.mine() }),
  });
}

/** 清空购物车 —— `DELETE /api/cart/clear`（C6）。 */
export function useClearCart() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => apiClient<void>('/api/cart/clear', { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.cart.mine() }),
  });
}
