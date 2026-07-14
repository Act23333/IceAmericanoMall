'use client';

import { create } from 'zustand';
import { apiClient, getAccessToken, scheduleProactiveRefresh, clearProactiveRefresh, type UserInfoResp } from '@icedmall/api';

interface User {
  userId: string;
  username: string;
  phone?: string;
  avatar?: string;
  balance?: number;
}

interface AuthState {
  user: User | null;
  isLoading: boolean;
  setUser: (user: User | null) => void;
  logout: () => void;
  /** 从 token cookie 恢复用户信息（页面刷新 / 首次加载时调用） */
  hydrate: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  isLoading: true,
  setUser: (user) => set({ user, isLoading: false }),
  logout: () => { clearProactiveRefresh(); set({ user: null, isLoading: false }); },
  hydrate: async () => {
    const token = getAccessToken();
    if (token) scheduleProactiveRefresh(token);
    if (!token) {
      set({ user: null, isLoading: false });
      return;
    }
    try {
      const info = await apiClient<UserInfoResp>('/api/user/info');
      set({
        user: {
          userId: String(info.userId ?? ''),
          username: info.username ?? '',
          phone: info.phone,
          avatar: info.avatar,
          balance: info.balance,
        },
        isLoading: false,
      });
    } catch {
      // API 不可用时以 token 存在为据，设置最小用户态（Navbar 据此显示已登录）
      set({ user: { userId: '', username: '' }, isLoading: false });
    }
  },
}));
