import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type { ApiResponse } from '@/domain/types';
import type { Collection, CollectionCreate, CollectionSummary } from '@/domain/collection';

export function useCollections() {
  return useQuery({
    queryKey: ['collections'],
    queryFn: async () => {
      const res = await api.get<ApiResponse<Collection[]>>('/collections');
      return res.data.data ?? [];
    },
  });
}

export function useCachedCollection(id: number): Collection | undefined {
  const qc = useQueryClient();
  for (const [, list] of qc.getQueriesData<Collection[]>({ queryKey: ['collections'] })) {
    const found = list?.find((c) => c.id === id);
    if (found) return found;
  }
  return undefined;
}

export function useCollectionSummary(id: number) {
  return useQuery({
    queryKey: ['collection-summary', id],
    queryFn: async () => {
      const res = await api.get<ApiResponse<CollectionSummary>>(`/collections/${id}/summary`);
      if (!res.data.data) throw new Error(res.data.error?.message ?? '요약을 불러오지 못했습니다.');
      return res.data.data;
    },
  });
}

export function useCreateCollection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CollectionCreate) => {
      const res = await api.post<ApiResponse<Collection>>('/collections', body);
      if (!res.data.data) throw new Error(res.data.error?.message ?? '묶음 저장에 실패했습니다.');
      return res.data.data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['collections'] }),
  });
}

export function useUpdateCollection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, body }: { id: number; body: CollectionCreate }) => {
      const res = await api.put<ApiResponse<Collection>>(`/collections/${id}`, body);
      if (!res.data.data) throw new Error(res.data.error?.message ?? '묶음 수정에 실패했습니다.');
      return res.data.data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['collections'] }),
  });
}

export function useDeleteCollection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.delete(`/collections/${id}`).then(() => undefined),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['collections'] }),
  });
}
