'use client';

import { create } from 'zustand';

/* ================================================================
   本地购物车 Store — Zustand（**仅未登录 / 离线降级**用）
   ================================================================
   定位（本批改造后）：**服务端购物车为准，客户端只是视图**（见 `cart-model.ts`）。
   本 store 只承担两件事：
     1. **未登录**用户的购物车（localStorage 持久化，未登录可用性不受影响）；
     2. 已登录但服务端不可达时的**降级**读源（离线可用）。
   已登录且服务端可用时，数据源是 `GET /api/cart`，本 store 不参与渲染。

   命名：本 store 是**前端自有域模型**（不属于后端报文），标识符保持 camelCase；
   但词汇与 SPU 实体对齐 —— `spuName`（原 `productName`）/ `spuId`
   （对齐 ADR-059 的 SPU/SKU 词汇消歧；报文侧 `spu_name` 见 `@icedmall/api` types）。

   `skuId` 是 **SKU 业务键 32-hex `String`**（与 `/api/cart/item/**` 入参、`CartItemVO.sku_id` 同型同义）。
   ================================================================ */

export interface CartItem {
  /** SKU 业务键（32-hex）。 */
  skuId: string;
  /** SPU 业务键（32-hex）；商品页加购时可带上，用于券范围预过滤。 */
  spuId?: string | null;
  /** SPU 名称。 */
  spuName: string;
  spec: string;
  image: string;
  price: number; // cents
  quantity: number;
  selected: boolean;
  createTime?: string; // 加入购物车时间
}

interface CartState {
  items: CartItem[];
  addItem: (item: Omit<CartItem, 'quantity' | 'selected'> & { quantity?: number }) => void;
  removeItem: (skuId: string) => void;
  updateQuantity: (skuId: string, quantity: number) => void;
  toggleSelect: (skuId: string) => void;
  selectAll: () => void;
  deselectAll: () => void;
  clearCart: () => void;
}

const STORAGE_KEY = 'icedmall-cart';

function loadPersisted(): CartItem[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as CartItem[]) : [];
  } catch {
    return [];
  }
}

function persist(items: CartItem[]) {
  if (typeof window === 'undefined') return;
  localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
}

export const useCartStore = create<CartState>((set, get) => ({
  items: loadPersisted(),

  addItem: (item) => {
    const { items } = get();
    const idx = items.findIndex((i) => i.skuId === item.skuId);
    const qty = item.quantity ?? 1;
    if (idx >= 0) {
      // 同 SKU 合并为增数量（与后端 `(user_id, sku_id)` 唯一语义同口径）
      const updated = [...items];
      const prev = updated[idx]!;
      updated[idx] = { ...prev, quantity: prev.quantity + qty };
      persist(updated);
      set({ items: updated });
    } else {
      const updated = [...items, { ...item, quantity: qty, selected: true }];
      persist(updated);
      set({ items: updated });
    }
  },

  removeItem: (skuId) => {
    const updated = get().items.filter((i) => i.skuId !== skuId);
    persist(updated);
    set({ items: updated });
  },

  updateQuantity: (skuId, quantity) => {
    if (quantity <= 0) {
      get().removeItem(skuId);
      return;
    }
    const updated = get().items.map((i) =>
      i.skuId === skuId ? { ...i, quantity } : i,
    );
    persist(updated);
    set({ items: updated });
  },

  toggleSelect: (skuId) => {
    const updated = get().items.map((i) =>
      i.skuId === skuId ? { ...i, selected: !i.selected } : i,
    );
    persist(updated);
    set({ items: updated });
  },

  selectAll: () => {
    const updated = get().items.map((i) => ({ ...i, selected: true }));
    persist(updated);
    set({ items: updated });
  },

  deselectAll: () => {
    const updated = get().items.map((i) => ({ ...i, selected: false }));
    persist(updated);
    set({ items: updated });
  },

  clearCart: () => {
    persist([]);
    set({ items: [] });
  },
}));
