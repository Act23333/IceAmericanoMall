/**
 * Server-side API 数据获取
 *
 * Next.js Server Components 专用。
 * 使用 Next.js 扩展的 fetch (自动缓存, ISR revalidate)。
 *
 * 与 client.ts (apiClient) 的区别:
 * - 不走 credentials: 'include' (服务端没有浏览器 cookie)
 * - 公开端点无需 JWT (商品列表, 详情, 分类)
 * - 利用 Next.js fetch 缓存机制
 *
 * ⚠️ **SPU 资源段与路径变量（2026-10-18 B8，ADR-062 §决策 2/3）**
 *   - 资源段 `/api/item/product/**` → **`/api/item/spu/**`**（与表 `spu` / 聚合 `Spu` /
 *     业务键列 `spu.spu_id` 同词）。⚠️ 名字里带 `product` 的**非 SPU 段**不属本口径、
 *     一律照旧（实测均在 trade-service：`/api/trade/seller/dashboard/product-stats`、
 *     `/api/trade/admin/stats/top-products`）。
 *   - 详情路径变量 `{spuId}` 现为 **SPU 业务键 32-hex `String`**（`SpuController` 以
 *     `IdGenerator#requireWire` 做形态校验，非法即 400）—— 不再是技术键 `Long`。
 *     故前端可直接用 `SpuVO.spu_id` 寻址，**回链能力因此恢复**。
 *
 * ⚠️ **P2「商品不存在」= 真 HTTP 404**（后端 `265543d6` 起，`NO_RESOURCE_FOUND(1103)`）：
 *   不再是「HTTP 200 + `code=3011`」。`serverFetch` 在 `!res.ok` 时抛
 *   `ServerFetchError(res.status, …)`，故 404 的 `code` 为 **404**；
 *   判定统一走 `isNotFound(err)`（`error-codes.ts`，404 / 1103 / 3011 三态并轨）。
 *   调用方（`app/spu/[spuId]/page.tsx`）据此 **只把 404 转成「商品不存在/已删除」**，
 *   其余错误继续上抛给 `error.tsx`（「暂时无法加载」+ 重试），不要混为一谈。
 */

import type { ProductVO, CategoryVO, PageResult } from './types';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://192.168.10.1:8080';

interface ApiResult<T> {
  code: number;
  msg: string;
  data: T;
}

/**
 * 服务端 fetch 失败。
 *
 * `code` 的取值口径（与 `ApiError` 保持一致的可判定性）：
 *   - HTTP 层失败（`!res.ok`）→ `code = res.status`（**P2 的 404 走这里**）；
 *   - 信封层失败（HTTP 200 但 `code !== 200/0`）→ `code = body.code`（3xxx 业务码）。
 * `isNotFound()` / `getUserMessage()` 对两种形态都能判定。
 */
export class ServerFetchError extends Error {
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
 * @param path  API 路径 (如 /api/item/spu/page)
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
    // ⚠️ P2 的「商品不存在」在这里（HTTP 404）—— 不要在这一层兜成通用错误，
    // 交由页面按 `isNotFound()` 分支渲染「商品不存在/已删除」。
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

/** 分页 SPU 列表 (ISR: 60s) —— P1 `GET /api/item/spu/page`。 */
export async function getProducts(params: {
  categoryId?: number;
  keyword?: string;
  sort?: string;
  order?: string;
  page?: number;
  size?: number;
}): Promise<PageResult<ProductVO>> {
  const sp = new URLSearchParams();
  // 后端 `SpuPageReq` 经 **`@QueryModel`** 绑定（Jackson + 全局 SNAKE_CASE）→ 线上名 snake_case
  // （契约 §P1：`category_id?` / `seller_id?`）。camelCase `categoryId` 会被静默忽略（2026-10-15 修复）。
  if (params.categoryId) sp.set('category_id', String(params.categoryId));
  if (params.keyword) sp.set('keyword', params.keyword);
  if (params.sort) sp.set('sort', params.sort);
  if (params.order) sp.set('order', params.order);
  sp.set('page', String(params.page ?? 1));
  sp.set('size', String(params.size ?? 20));
  return serverFetch<PageResult<ProductVO>>(`/api/item/spu/page?${sp.toString()}`, 60);
}

/**
 * SPU 详情 (ISR: 120s) —— P2 `GET /api/item/spu/{spuId}`。
 *
 * @param spuId **SPU 业务键 32-hex**（`SpuVO.spu_id`，契约 §P2 / §1.8）。
 *   形参名用 `spuId` 而非 `productId`：它就是业务键，与技术键 `SpuVO.id` 无关。
 *   ⚠️ 传入非 32-hex 形态 → 后端 `requireWire` fail-fast **400**（不是 404）。
 */
export async function getProduct(spuId: string): Promise<ProductVO> {
  return serverFetch<ProductVO>(`/api/item/spu/${spuId}`, 120);
}

/**
 * SPU 详情 — **按短码**（P38 `GET /api/item/spu/by-code/{code}`，出参与 P2 同形）。
 *
 * 用途：小程序码 `scene = "p=<短码>"` 的落地页把短码换回 SPU，再用返回的 `spu_id`
 * 继续正常调用。失败语义：短码形态非法 → 400 `PARAM_ERROR(1101)`；
 * 短码不存在 / 商品已删除 → 404 `NO_RESOURCE_FOUND(1103)`；已下架 → 200 + `status`。
 *
 * ⚠️ 短码**不是实体标识**，不得替代 `spu_id`（ADR-063 §决策 8）——
 *   换回来的第一个动作就是取 `SpuVO.spu_id`。
 */
export async function getSpuByCode(code: string): Promise<ProductVO> {
  return serverFetch<ProductVO>(`/api/item/spu/by-code/${code}`, 120);
}

/** 分类树 (ISR: 300s — 类目极少变化) */
export async function getCategories(): Promise<CategoryVO[]> {
  return serverFetch<CategoryVO[]>('/api/item/category/tree', 300);
}
