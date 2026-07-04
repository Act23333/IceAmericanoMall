'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient, type UserInfoResp } from '@icedmall/api';
import { queryKeys } from '../queries';

export function useUserInfo() {
  return useQuery<UserInfoResp>({
    queryKey: queryKeys.user.profile(),
    queryFn: () => apiClient<UserInfoResp>('/api/user/info'),
    staleTime: 60 * 1000,
  });
}
