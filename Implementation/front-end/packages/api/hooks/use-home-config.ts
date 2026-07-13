'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../client';

export interface HomeConfigItem {
  id: number;
  /** 后端发 Integer，对应 HomeConfigEntity.type */
  type: number;
  title: string;
  /** 后端字段名 image，对应 HomeConfigEntity.image */
  image: string;
  linkUrl: string;
  sortOrder: number;
}

export function useHomeConfig() {
  return useQuery<HomeConfigItem[]>({
    queryKey: ['home', 'config'],
    queryFn: () => apiClient<HomeConfigItem[]>('/api/home/config'),
    staleTime: 5 * 60 * 1000,
  });
}
