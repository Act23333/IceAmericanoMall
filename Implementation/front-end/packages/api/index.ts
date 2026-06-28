/**
 * @icedmall/api — 共享 API 层
 *
 * 提供：
 * 1. TypeScript 类型定义（从后端 OpenAPI spec 生成）
 * 2. Fetch 客户端 — apiClient (浏览器) + server.ts (Server Components)
 * 3. TanStack Query hooks（缓存/去重/乐观更新）
 * 4. Query key 工厂（统一缓存键管理）
 */

export * from './types';
export * from './hooks';
export * from './queries';
export { apiClient, ApiError } from './client';

// 服务端数据获取 (Server Components)
export { getProducts, getProduct, getCategories } from './server';
