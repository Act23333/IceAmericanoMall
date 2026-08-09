'use client';

import { create } from 'zustand';

/* ================================================================
   购物车 Store — Zustand
   匿名用户: localStorage 持久化
   登录用户: 同步到服务端 (TODO: Phase 4 API integration)
   ================================================================ */

export interface CartItem {
  skuId: string;
  productName: string;
  spec: string;
  image: string;
  price: number; // cents
  quantity: number;
  selected: boolean;
  createTime?: string; // V5.0: 加入购物车时间
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

function loadPersisted(): CartItem[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = localStorage.getItem('icedmall-cart');
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

function persist(items: CartItem[]) {
  if (typeof window === 'undefined') return;
  localStorage.setItem('icedmall-cart', JSON.stringify(items));
}

export const useCartStore = create<CartState>((set, get) => ({
  items: loadPersisted(),

  addItem: (item) => {
    const { items } = get();
    const idx = items.findIndex((i) => i.skuId === item.skuId);
    if (idx >= 0) {
      const updated = [...items];
      updated[idx] = {
        ...updated[idx]!,
        quantity: updated[idx]!.quantity + (item.quantity ?? 1),
      };
      persist(updated);
      set({ items: updated });
    } else {
      const updated = [...items, { ...item, quantity: item.quantity ?? 1, selected: true }];
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
