'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type AddressResp, type AddressReq } from '@icedmall/api';
import { queryKeys } from '../queries';

export function useAddresses() {
  return useQuery<AddressResp[]>({
    queryKey: queryKeys.user.addresses(),
    queryFn: () => apiClient<AddressResp[]>('/api/user/address/list'),
    staleTime: 30 * 1000,
  });
}

export function useAddAddress() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: AddressReq) =>
      apiClient<void>('/api/user/address/add', { method: 'POST', body: JSON.stringify(req) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.user.addresses() }),
  });
}

export function useUpdateAddress() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...req }: AddressReq & { id: number }) =>
      apiClient<void>(`/api/user/address/update/${id}`, { method: 'PUT', body: JSON.stringify(req) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.user.addresses() }),
  });
}

export function useDeleteAddress() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => apiClient<void>(`/api/user/address/delete/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.user.addresses() }),
  });
}
