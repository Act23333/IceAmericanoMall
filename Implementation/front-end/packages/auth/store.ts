'use client';

import { create } from 'zustand';
import { apiClient, getAccessToken, scheduleProactiveRefresh, clearProactiveRefresh, type UserInfoResp } from '@icedmall/api';

/**
 * 认证 store。
 *
 * ⚠️ 边界：`User` 是**前端内部视图模型**（供 Navbar / 个人中心消费），
 * 字段保持 camelCase；只有 `hydrate()` 里从 `UserInfoResp`（报文，snake_case）
 * 取值的那一处是映射点。
 *
 * 注：`UserInfoResp` 自 V5.4 起**不含** `balance`（余额归 wallet-service，
 * 取 `GET /api/wallet`）—— 故本 store 不再携带 balance。
 */
interface User {
  userId: string;
  username: string;
  phone?: string | null;
  avatar?: string | null;
}

interface AuthState {
  user: User | null;
  isLoading: boolean;
  setUser: (user: User | null) => void;
  logout: () => void;
  /** 从 token cookie 恢复用户信息（页面刷新 / 首次加载时调用） */
  hydrate: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set, _get) => ({
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
      // 报文 → 视图模型（snake_case → camelCase 的显式映射点）
      const info = await apiClient<UserInfoResp>('/api/user/info');
      set({
        user: {
          userId: String(info.user_id ?? ''),
          username: info.username ?? '',
          phone: info.phone,
          avatar: info.avatar,
        },
        isLoading: false,
      });
    } catch {
      // API 不可用时以 token 存在为据，设置最小用户态（Navbar 据此显示已登录）
      set({ user: { userId: '', username: '' }, isLoading: false });
    }
  },
}));
