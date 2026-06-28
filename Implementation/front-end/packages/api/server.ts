/**
 * Server-side API 数据获取
 *
 * Next.js Server Components 专用。
 * 使用 Next.js 扩展的 fetch (自动缓存, ISR revalidate)。
 *
 * 与 client.ts (apiClient) 的区别:
 * - 不走 credentials: 'include' (服务端没有浏览器 cookie)
 * - 公开端点无需 JWT (product listing, detail, categories)
 * - 利用 Next.js fetch 缓存机制
 */

import type { ProductVO, CategoryVO, PageResult } from './types';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://gate-service:8080';

interface ApiResult<T> {
  code: number;
  msg: string;
  data: T;
}

class ServerFetchError extends Error {
  constructor(
    public code: number,
    message: string,
  ) {
    super(message);
    this.name = 'ServerFetchError';
  }
}

/**
 * 服务端 fetch — Next.js 扩展缓存
 *
 * @param path  API 路径 (如 /api/item/product/page)
 * @param revalidate ISR 缓存秒数 (默认 60s)
 */
async function serverFetch<T>(
  path: string,
  revalidate = 60,
): Promise<T> {
  const url = `${API_BASE}${path}`;
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    next: { revalidate },
  });

  if (!res.ok) {
    throw new ServerFetchError(res.status, `API ${path} 返回 HTTP ${res.status}`);
  }

  const body: ApiResult<T> = await res.json();

  if (body.code !== 200 && body.code !== 0) {
    throw new ServerFetchError(body.code, body.msg || 'Unknown error');
  }

  return body.data;
}

/* ================================================================
   Public API — Server Components 直接调用
   ================================================================ */

/** 分页商品列表 (ISR: 60s) */
export async function getProducts(params: {
  categoryId?: number;
  keyword?: string;
  sort?: string;
  order?: string;
  page?: number;
  size?: number;
}): Promise<PageResult<ProductVO>> {
  const sp = new URLSearchParams();
  if (params.categoryId) sp.set('categoryId', String(params.categoryId));
  if (params.keyword) sp.set('keyword', params.keyword);
  if (params.sort) sp.set('sort', params.sort);
  if (params.order) sp.set('order', params.order);
  sp.set('page', String(params.page ?? 1));
  sp.set('size', String(params.size ?? 20));
  return serverFetch<PageResult<ProductVO>>(`/api/item/product/page?${sp.toString()}`, 60);
}

/** 商品详情 (ISR: 120s) */
export async function getProduct(productId: string): Promise<ProductVO> {
  return serverFetch<ProductVO>(`/api/item/product/${productId}`, 120);
}

/** 分类树 (ISR: 300s — 类目极少变化) */
export async function getCategories(): Promise<CategoryVO[]> {
  return serverFetch<CategoryVO[]>('/api/item/category/tree', 300);
}
