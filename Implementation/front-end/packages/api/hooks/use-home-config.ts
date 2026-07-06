'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../client';

export interface HomeConfigItem {
  id: number;
  type: string;
  title: string;
  imageUrl: string;
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
