'use client';

import { create } from 'zustand';

interface User {
  userId: string;
  username: string;
}

interface AuthState {
  user: User | null;
  isLoading: boolean;
  setUser: (user: User | null) => void;
  logout: () => void;
}

/** 客户端认证状态 — Token 在 httpOnly Cookie，此 Store 仅缓存用户信息 */
export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isLoading: true,
  setUser: (user) => set({ user, isLoading: false }),
  logout: () => set({ user: null, isLoading: false }),
}));
