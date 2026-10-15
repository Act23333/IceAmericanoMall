'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient, type HomeConfigVO } from '@icedmall/api';

/** 首页装修位配置 —— `GET /api/home/config`，响应 `HomeConfigVO[]`。 */
export function useHomeConfig() {
  return useQuery<HomeConfigVO[]>({
    queryKey: ['home', 'config'],
    queryFn: () => apiClient<HomeConfigVO[]>('/api/home/config'),
    staleTime: 5 * 60 * 1000,
  });
}
