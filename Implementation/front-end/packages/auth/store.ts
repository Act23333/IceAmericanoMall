'use client';

import { create } from 'zustand';

interface User {
  userId: string;
  username: string;
  nickname: string;
  role: string;
}

interface AuthState {
  user: User | null;
  isLoading: boolean;
  setUser: (user: User | null) => void;
  logout: () => void;
}

/**
 * 客户端认证状态 — Zustand
 *
 * 注意：Token 存储在 httpOnly Cookie，
 * 此 Store 仅缓存当前用户基本信息（从 JWT payload 解析）。
 */
export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isLoading: true,
  setUser: (user) => set({ user, isLoading: false }),
  logout: () => set({ user: null, isLoading: false }),
}));
