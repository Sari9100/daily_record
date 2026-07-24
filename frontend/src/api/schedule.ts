import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type { ApiResponse } from '@/domain/types';
import type { FamilyMember, Schedule, ScheduleCreate, ScheduleType } from '@/domain/schedule';

export type ScheduleListParams = {
  from?: string;
  to?: string;
  type?: ScheduleType;
  scope?: string;
  q?: string;
};

async function fetchSchedules(params: ScheduleListParams): Promise<Schedule[]> {
  const res = await api.get<ApiResponse<Schedule[]>>('/schedules', { params });
  if (!res.data.data) {
    throw new Error(res.data.error?.message ?? '일정을 불러오지 못했습니다.');
  }
  return res.data.data;
}

export function useSchedules(params: ScheduleListParams, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['schedules', params],
    queryFn: () => fetchSchedules(params),
    enabled: options?.enabled,
  });
}

/** 단건 GET 엔드포인트가 없어 목록 캐시에서 일정을 찾아 편집 prefill. */
export function useCachedSchedule(id: number): Schedule | undefined {
  const qc = useQueryClient();
  const queries = qc.getQueriesData<Schedule[]>({ queryKey: ['schedules'] });
  for (const [, list] of queries) {
    const found = list?.find((s) => s.id === id);
    if (found) return found;
  }
  return undefined;
}

function invalidate(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: ['schedules'] });
  qc.invalidateQueries({ queryKey: ['timeline'] });
}

export function useCreateSchedule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: ScheduleCreate) => {
      const res = await api.post<ApiResponse<Schedule>>('/schedules', body);
      if (!res.data.data) throw new Error(res.data.error?.message ?? '일정 저장에 실패했습니다.');
      return res.data.data;
    },
    onSuccess: () => invalidate(qc),
  });
}

export function useUpdateSchedule(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: ScheduleCreate) => {
      const res = await api.put<ApiResponse<Schedule>>(`/schedules/${id}`, body);
      if (!res.data.data) throw new Error(res.data.error?.message ?? '일정 수정에 실패했습니다.');
      return res.data.data;
    },
    onSuccess: () => invalidate(qc),
  });
}

export function useDeleteSchedule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.delete(`/schedules/${id}`).then(() => undefined),
    onSuccess: () => invalidate(qc),
  });
}

export function useToggleDone() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.patch(`/schedules/${id}/done`).then(() => undefined),
    onSuccess: () => invalidate(qc),
  });
}

async function fetchFamilyMembers(): Promise<FamilyMember[]> {
  const res = await api.get<ApiResponse<{ members: FamilyMember[] }>>('/family');
  return res.data.data?.members ?? [];
}

export function useFamilyMembers() {
  return useQuery({ queryKey: ['family', 'members'], queryFn: fetchFamilyMembers });
}
