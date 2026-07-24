import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type { ApiResponse } from '@/domain/types';

export type Tag = { id: number; name: string };

export function useTags() {
  return useQuery({
    queryKey: ['tags'],
    queryFn: async () => {
      const res = await api.get<ApiResponse<Tag[]>>('/tags');
      return res.data.data ?? [];
    },
  });
}

/** 태그 즉석 생성(묶음 생성 미니폼 등에서 사용). 가족 내 이름 중복 시 409. */
export function useCreateTag() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (name: string) => {
      const res = await api.post<ApiResponse<Tag>>('/tags', { name });
      if (!res.data.data) throw new Error(res.data.error?.message ?? '태그 생성에 실패했습니다.');
      return res.data.data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tags'] }),
  });
}
