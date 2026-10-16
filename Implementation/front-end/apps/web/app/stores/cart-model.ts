'use client';

/* ================================================================
   购物车读模型 —— **服务端为准，客户端只是视图**

   ── 为什么存在（本批最大件，2026-10-17 B3）────────────────────────
   此前 `/api/cart/**` 在 Web 端 **0 命中**：加购/改量/勾选只写本地 Zustand，
   而下单 `CreateOrderReq` **不承载购物车项 ID**（`cart_item_ids` 已于 V5.4 删除并保持废弃）
   —— 后端下单**恒取服务端购物车已勾选项**（契约 §4.6.1 T1 明注）。
   两者互不认识 ⇒ **主链路断裂**：本地加了商品，提交订单必然「购物车为空」。

   裁决口径（大厂口径，已定）：**服务端购物车为准，客户端只是视图**；
   **不改后端**去接受客户端直传。

   ── 数据源选择（三态，见 `source` / `degraded`）──────────────────
   | 登录态 | 服务端状态 | `source` | 行为 |
   | :-- | :-- | :-- | :-- |
   | 未登录 | — | `local` | 只读本地 Zustand（localStorage）—— 未登录可用性不受影响 |
   | 已登录 | 查询成功 | `server` | 以 `GET /api/cart` 为准；所有写操作走 `/api/cart/**` |
   | 已登录 | 查询失败 | `local` + `degraded` | 降级读本地（离线可用），**不静默伪装成服务端成功** |

   ── 不变量（改造不得破坏）──────────────────────────────────────
   1. **未登录可用性**：未登录时行为与本批之前等价（纯本地）；
   2. **加购即时反馈**：走**乐观更新**（先写 query 缓存 → 再发请求 → 失败回滚），
      按钮状态不等待网络往返；
   3. **SKU 去重合并语义**：`(user_id, sku_id)` 唯一，同 SKU 再加 = **增数量**，
      乐观合并与后端 `CartManager` 同口径（不产生第二行）。

   ── 已知端点缺口（按契约，不臆造）──────────────────────────────
   - C8 `PATCH /api/cart/select-all`：⚪ **未实现** → `selectAll` / `deselectAll`
     对已登录用户**按行扇出** C5（仅发需要变更的行，不重复发）。
   - C9 `DELETE /api/cart/batch`：⚪ 未实现 → 无批量删除。
   - C10 `POST /api/cart/item/{skuId}/move-to-favorite`：⚪ **未实现**
     （实测后端 `CartController` 零命中该路由）→ **不对接**，故本模型**不提供**移入收藏。
   ================================================================ */

import { useCallback, useEffect, useMemo, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import {
  useCart,
  useAddToCart,
  useUpdateCartItem,
  useRemoveCartItem,
  useToggleSelect,
  useClearCart,
  queryKeys,
  type CartVO,
  type CartItemVO,
} from '@icedmall/api';
import { useAuthStore } from '@icedmall/auth';
import { useCartStore, type CartItem } from './cart-store';

/** 视图中一行购物车（前端内部视图模型 —— 标识符 camelCase，取值处即报文映射点）。 */
export interface CartLine {
  /** SKU 业务键（32-hex）。可直接用于 C4/C5 路径变量。 */
  skuId: string;
  /** SPU 业务键（32-hex）；本地行无此字段时为 null。 */
  spuId: string | null;
  spuName: string;
  spec: string;
  image: string;
  /** 单价（分）。 */
  price: number;
  quantity: number;
  selected: boolean;
  /** 小计（分）。 */
  subTotal: number;
  createTime?: string;
}

/** 当前数据源。 */
export type CartSource = 'server' | 'local';

export interface CartModel {
  lines: CartLine[];
  source: CartSource;
  /** 已登录但服务端不可达 → 已降级读本地（**离线可用，非静默**）。 */
  degraded: boolean;
  isLoading: boolean;
  selectedLines: CartLine[];
  selectedCount: number;
  /** 已选商品总额（分）。 */
  selectedTotalCents: number;
  allSelected: boolean;
  add: (input: AddInput) => Promise<void>;
  remove: (skuId: string) => Promise<void>;
  setQuantity: (skuId: string, quantity: number) => Promise<void>;
  toggleSelect: (skuId: string) => Promise<void>;
  selectAll: () => Promise<void>;
  deselectAll: () => Promise<void>;
  clear: () => Promise<void>;
}

export interface AddInput {
  skuId: string;
  spuId?: string | null;
  spuName: string;
  spec: string;
  image: string;
  price: number;
  quantity?: number;
}

/* ────────────────── 本地 ←→ 视图模型映射 ────────────────── */

const toLineFromLocal = (i: CartItem): CartLine => ({
  skuId: i.skuId,
  spuId: i.spuId ?? null,
  spuName: i.spuName,
  spec: i.spec,
  image: i.image,
  price: i.price,
  quantity: i.quantity,
  selected: i.selected,
  subTotal: i.price * i.quantity,
  createTime: i.createTime,
});

/** 报文 `CartItemVO` → 视图模型（**只在此处**发生字段映射）。 */
const toLineFromServer = (i: CartItemVO): CartLine => ({
  skuId: i.sku_id,
  spuId: i.spu_id,
  // `spu_name` 为读时回填、fail-open → null（SKU 已删除/降级）；不回落技术 id，空文案由页面兜底
  spuName: i.spu_name ?? '',
  spec: i.spec,
  image: i.image,
  price: i.price,
  quantity: i.quantity,
  selected: i.selected,
  subTotal: i.sub_total,
});

/* ────────────────── 乐观缓存构造（与后端口径镜像） ────────────────── */

function recompute(items: CartItemVO[]): CartVO {
  return {
    items,
    total_price: items.reduce((s, i) => s + i.sub_total, 0),
    selected_price: items.filter((i) => i.selected).reduce((s, i) => s + i.sub_total, 0),
    // 服务端 `all_selected` 还要求「全部可计价（回填命中）」；乐观值只求近似，回填后即被替换
    all_selected: items.length > 0 && items.every((i) => i.selected),
  };
}

/** 乐观加购 —— 同 SKU 合并为增数量（镜像服务端 `(user_id, sku_id)` 唯一语义）。 */
function optimisticAdd(prev: CartVO | undefined, input: AddInput): CartVO {
  const qty = input.quantity ?? 1;
  const items = prev?.items ?? [];
  const idx = items.findIndex((i) => i.sku_id === input.skuId);
  if (idx >= 0) {
    return recompute(
      items.map((i, n) =>
        n === idx
          ? { ...i, quantity: i.quantity + qty, sub_total: i.price * (i.quantity + qty) }
          : i,
      ),
    );
  }
  const row: CartItemVO = {
    sku_id: input.skuId,
    spu_id: input.spuId ?? null,
    spu_name: input.spuName,
    spec: input.spec,
    image: input.image,
    price: input.price,
    quantity: qty,
    selected: true,
    sub_total: input.price * qty,
  };
  return recompute([...items, row]);
}

/* ────────────────── 读模型 hook ────────────────── */

export function useCartModel(): CartModel {
  const qc = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const loggedIn = !!user;

  // ⚠️ 未登录**必须**关掉请求：`/api/cart` 是 🔒 端点，匿名请求会 401 →
  // 刷新兜底失败 → 广播 `auth:logout` → 把访客弹到登录页（破坏「未登录可用性」）。
  const serverQuery = useCart({ enabled: loggedIn });
  const serverData = loggedIn ? serverQuery.data : undefined;

  const localItems = useCartStore((s) => s.items);
  const localAdd = useCartStore((s) => s.addItem);
  const localRemove = useCartStore((s) => s.removeItem);
  const localSetQty = useCartStore((s) => s.updateQuantity);
  const localToggle = useCartStore((s) => s.toggleSelect);
  const localSelectAll = useCartStore((s) => s.selectAll);
  const localDeselectAll = useCartStore((s) => s.deselectAll);
  const localClear = useCartStore((s) => s.clearCart);

  const addServer = useAddToCart();
  const updateServer = useUpdateCartItem();
  const removeServer = useRemoveCartItem();
  const toggleServer = useToggleSelect();
  const clearServer = useClearCart();

  const useServer = loggedIn && !!serverData;
  const degraded = loggedIn && !serverData && (serverQuery.isError || (!serverQuery.isLoading && !serverQuery.data));

  const lines = useMemo<CartLine[]>(
    () => (useServer
      ? (serverData!.items ?? []).map(toLineFromServer)
      : localItems.map(toLineFromLocal)),
    [useServer, serverData, localItems],
  );

  const selectedLines = useMemo(() => lines.filter((l) => l.selected), [lines]);
  const selectedTotalCents = useMemo(
    () => selectedLines.reduce((s, l) => s + l.subTotal, 0),
    [selectedLines],
  );

  /* —— 写操作：登录走服务端（乐观 + 回滚），未登录走本地 —— */

  const cacheKey = queryKeys.cart.mine();

  const add = useCallback(
    async (input: AddInput) => {
      if (!loggedIn) {
        localAdd({
          skuId: input.skuId,
          spuId: input.spuId ?? null,
          spuName: input.spuName,
          spec: input.spec,
          image: input.image,
          price: input.price,
          quantity: input.quantity ?? 1,
        });
        return;
      }
      await qc.cancelQueries({ queryKey: cacheKey });
      const prev = qc.getQueryData<CartVO>(cacheKey);
      qc.setQueryData<CartVO>(cacheKey, optimisticAdd(prev, input)); // 即时反馈
      try {
        await addServer.mutateAsync({ sku_id: input.skuId, quantity: input.quantity ?? 1 });
      } catch (e) {
        qc.setQueryData<CartVO>(cacheKey, prev); // 回滚路径
        throw e;
      } finally {
        qc.invalidateQueries({ queryKey: cacheKey });
      }
    },
    [loggedIn, localAdd, qc, cacheKey, addServer],
  );

  const remove = useCallback(
    async (skuId: string) => {
      if (!loggedIn) {
        localRemove(skuId);
        return;
      }
      await qc.cancelQueries({ queryKey: cacheKey });
      const prev = qc.getQueryData<CartVO>(cacheKey);
      if (prev) {
        qc.setQueryData<CartVO>(cacheKey, recompute(prev.items.filter((i) => i.sku_id !== skuId)));
      }
      try {
        await removeServer.mutateAsync(skuId);
      } catch (e) {
        qc.setQueryData<CartVO>(cacheKey, prev);
        throw e;
      } finally {
        qc.invalidateQueries({ queryKey: cacheKey });
      }
    },
    [loggedIn, localRemove, qc, cacheKey, removeServer],
  );

  const setQuantity = useCallback(
    async (skuId: string, quantity: number) => {
      if (quantity <= 0) return remove(skuId); // 与后端 C3「quantity ≤ 0 即删除」同口径
      if (!loggedIn) {
        localSetQty(skuId, quantity);
        return;
      }
      await qc.cancelQueries({ queryKey: cacheKey });
      const prev = qc.getQueryData<CartVO>(cacheKey);
      if (prev) {
        qc.setQueryData<CartVO>(
          cacheKey,
          recompute(prev.items.map((i) => (i.sku_id === skuId ? { ...i, quantity, sub_total: i.price * quantity } : i))),
        );
      }
      try {
        await updateServer.mutateAsync({ sku_id: skuId, quantity });
      } catch (e) {
        qc.setQueryData<CartVO>(cacheKey, prev);
        throw e;
      } finally {
        qc.invalidateQueries({ queryKey: cacheKey });
      }
    },
    [remove, loggedIn, localSetQty, qc, cacheKey, updateServer],
  );

  const toggleSelect = useCallback(
    async (skuId: string) => {
      if (!loggedIn) {
        localToggle(skuId);
        return;
      }
      const current = qc.getQueryData<CartVO>(cacheKey);
      const row = current?.items.find((i) => i.sku_id === skuId);
      const nextSelected = !(row?.selected ?? false);
      await qc.cancelQueries({ queryKey: cacheKey });
      const prev = current;
      if (prev) {
        qc.setQueryData<CartVO>(
          cacheKey,
          recompute(prev.items.map((i) => (i.sku_id === skuId ? { ...i, selected: nextSelected } : i))),
        );
      }
      try {
        await toggleServer.mutateAsync({ skuId, selected: nextSelected });
      } catch (e) {
        qc.setQueryData<CartVO>(cacheKey, prev);
        throw e;
      } finally {
        qc.invalidateQueries({ queryKey: cacheKey });
      }
    },
    [loggedIn, localToggle, qc, cacheKey, toggleServer],
  );

  /**
   * 全选 / 取消全选。
   * ⚠️ 契约 C8 `PATCH /api/cart/select-all` **未实现** → 已登录时按行扇出 C5，
   * 且**只发需要变更的行**（已满足目标态的行不回发，减少写放大）。
   */
  const setAllSelected = useCallback(
    async (selected: boolean) => {
      if (!loggedIn) {
        selected ? localSelectAll() : localDeselectAll();
        return;
      }
      await qc.cancelQueries({ queryKey: cacheKey });
      const prev = qc.getQueryData<CartVO>(cacheKey);
      if (prev) {
        qc.setQueryData<CartVO>(
          cacheKey,
          recompute(prev.items.map((i) => (i.selected === selected ? i : { ...i, selected }))),
        );
      }
      const targets = (prev?.items ?? []).filter((i) => i.selected !== selected);
      const results = await Promise.allSettled(
        targets.map((i) => toggleServer.mutateAsync({ skuId: i.sku_id, selected })),
      );
      if (results.some((r) => r.status === 'rejected')) {
        qc.setQueryData<CartVO>(cacheKey, prev); // 部分失败 → 整体回滚，交由调用方提示
        qc.invalidateQueries({ queryKey: cacheKey });
        throw results.find((r) => r.status === 'rejected')!.reason;
      }
      qc.invalidateQueries({ queryKey: cacheKey });
    },
    [loggedIn, localSelectAll, localDeselectAll, qc, cacheKey, toggleServer],
  );

  const clear = useCallback(async () => {
    if (!loggedIn) {
      localClear();
      return;
    }
    await qc.cancelQueries({ queryKey: cacheKey });
    const prev = qc.getQueryData<CartVO>(cacheKey);
    qc.setQueryData<CartVO>(cacheKey, recompute([]));
    try {
      await clearServer.mutateAsync();
    } catch (e) {
      qc.setQueryData<CartVO>(cacheKey, prev);
      throw e;
    } finally {
      qc.invalidateQueries({ queryKey: cacheKey });
    }
  }, [loggedIn, localClear, qc, cacheKey, clearServer]);

  return {
    lines,
    source: useServer ? 'server' : 'local',
    degraded,
    // 未登录不发请求 ⇒ 不是「加载中」
    isLoading: loggedIn && serverQuery.isLoading,
    selectedLines,
    selectedCount: selectedLines.reduce((s, l) => s + l.quantity, 0),
    selectedTotalCents,
    allSelected: lines.length > 0 && lines.every((l) => l.selected),
    add,
    remove,
    setQuantity,
    toggleSelect,
    selectAll: useCallback(() => setAllSelected(true), [setAllSelected]),
    deselectAll: useCallback(() => setAllSelected(false), [setAllSelected]),
    clear,
  };
}

/* ================================================================
   登录合并 —— 把**未登录期间**的本地购物车并入服务端购物车

   幂等设计（三道）：① 模块级运行锁（并发挂载只跑一次）；
   ② 每行**成功才移除**本地（部分失败 → 失败行留在本地，下次再并，不丢用户数据）；
   ③ 本地清空即天然幂等（重复运行找不到待并行）。
   `POST /api/cart/item` 的服务端语义正是「同 SKU 增数量」⇒ 合并 = 数量相加，**不产生第二行**。
   ================================================================ */

let mergeRunning = false;

export function useCartLoginMerge(): void {
  const user = useAuthStore((s) => s.user);
  const loggedIn = !!user;
  const userId = user?.userId ?? null;
  const qc = useQueryClient();
  const addServer = useAddToCart();
  const addRef = useRef(addServer.mutateAsync);
  addRef.current = addServer.mutateAsync;
  const started = useRef(false);

  /**
   * 账号切换即丢弃购物车缓存 —— `queryKeys.cart.mine()` 是**与用户无关**的键，
   * 若不清理，切到另一个账号后 10s staleTime 内会先渲染**上一个账号**的购物车。
   * 登出（userId → null）同样清理，避免残留数据被下次登录误读。
   */
  useEffect(() => {
    qc.removeQueries({ queryKey: queryKeys.cart.mine() });
  }, [userId, qc]);

  useEffect(() => {
    if (!loggedIn) {
      // 登出即复位：否则组件（挂在 Providers 上）不卸载，`started` 恒 true
      // ⇒ 用户「登出 → 本机加购 → 再登录」时**永远不会**再触发合并。
      started.current = false;
      return;
    }
    if (started.current || mergeRunning) return;
    const pending = useCartStore.getState().items;
    if (pending.length === 0) return;
    started.current = true;
    mergeRunning = true;
    void (async () => {
      const merged: string[] = [];
      for (const item of pending) {
        try {
          await addRef.current({ sku_id: item.skuId, quantity: item.quantity });
          merged.push(item.skuId);
        } catch {
          // 保留本地行：登录态但服务端不可达时不能把用户的购物车丢掉
        }
      }
      if (merged.length > 0) {
        for (const skuId of merged) useCartStore.getState().removeItem(skuId);
        qc.invalidateQueries({ queryKey: queryKeys.cart.mine() });
      }
      mergeRunning = false;
    })();
  }, [loggedIn, qc]);
}
