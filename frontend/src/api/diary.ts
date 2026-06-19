import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type { ApiResponse } from '@/domain/types';
import type { Diary, DiaryCreate } from '@/domain/diary';

export type DiaryListParams = { from?: string; to?: string; view?: string; scope?: string };

async function fetchDiaries(params: DiaryListParams): Promise<Diary[]> {
  const res = await api.get<ApiResponse<Diary[]>>('/diaries', { params });
  if (!res.data.data) {
    throw new Error(res.data.error?.message ?? '기록을 불러오지 못했습니다.');
  }
  return res.data.data;
}

export function useDiaries(params: DiaryListParams) {
  return useQuery({ queryKey: ['diaries', params], queryFn: () => fetchDiaries(params) });
}

export function useCachedDiary(id: number): Diary | undefined {
  const qc = useQueryClient();
  for (const [, list] of qc.getQueriesData<Diary[]>({ queryKey: ['diaries'] })) {
    const found = list?.find((d) => d.id === id);
    if (found) return found;
  }
  return undefined;
}

function invalidate(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: ['diaries'] });
  qc.invalidateQueries({ queryKey: ['timeline'] });
}

export function useCreateDiary() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: DiaryCreate) => {
      const res = await api.post<ApiResponse<Diary>>('/diaries', body);
      if (!res.data.data) throw new Error(res.data.error?.message ?? '기록 저장에 실패했습니다.');
      return res.data.data;
    },
    onSuccess: (created) => {
      // 생성 직후 편집(사진 추가) 화면이 캐시에서 찾을 수 있도록 즉시 주입한 뒤 백그라운드 갱신
      qc.setQueriesData<Diary[]>({ queryKey: ['diaries'] }, (old) => (old ? [created, ...old] : old));
      invalidate(qc);
    },
  });
}

export function useUpdateDiary(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: DiaryCreate) => {
      const res = await api.put<ApiResponse<Diary>>(`/diaries/${id}`, body);
      if (!res.data.data) throw new Error(res.data.error?.message ?? '기록 수정에 실패했습니다.');
      return res.data.data;
    },
    onSuccess: () => invalidate(qc),
  });
}

export function useDeleteDiary() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.delete(`/diaries/${id}`).then(() => undefined),
    onSuccess: () => invalidate(qc),
  });
}
