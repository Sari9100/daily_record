import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type { ApiResponse } from '@/domain/types';

export type DetailLevel = 'FULL' | 'SUMMARY';
export type PersonSetting = { sharedScheduleDetailLevel: DetailLevel };

export function useSettings() {
  return useQuery({
    queryKey: ['settings'],
    queryFn: async () => {
      const res = await api.get<ApiResponse<PersonSetting>>('/me/settings');
      return res.data.data ?? { sharedScheduleDetailLevel: 'FULL' as DetailLevel };
    },
  });
}

export function useUpdateSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (sharedScheduleDetailLevel: DetailLevel) => {
      const res = await api.put<ApiResponse<PersonSetting>>('/me/settings', { sharedScheduleDetailLevel });
      if (!res.data.data) throw new Error(res.data.error?.message ?? '설정 저장에 실패했습니다.');
      return res.data.data;
    },
    onSuccess: (data) => {
      qc.setQueryData(['settings'], data);
      qc.invalidateQueries({ queryKey: ['schedules'] });
    },
  });
}
