/**
 * TanStack Query hooks — 按领域拆分
 *
 * 每个 hook 封装：
 * - query key（配合 queries/key-factory）
 * - fetch 调用（通过 apiClient）
 * - 缓存策略（staleTime / gcTime）
 */

export { useCategories } from './use-categories';
export { useProducts, useProduct } from './use-products';
