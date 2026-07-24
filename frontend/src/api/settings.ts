import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type { ApiResponse } from '@/domain/types';

export type DetailLevel = 'FULL' | 'SUMMARY';
export type ViewMode = 'INLINE' | 'CALENDAR';
export type PersonSetting = {
  sharedScheduleDetailLevel: DetailLevel;
  ledgerDefaultView: ViewMode;
  scheduleDefaultView: ViewMode;
  diaryDefaultView: ViewMode;
};

const DEFAULT_SETTINGS: PersonSetting = {
  sharedScheduleDetailLevel: 'FULL',
  ledgerDefaultView: 'CALENDAR',
  scheduleDefaultView: 'CALENDAR',
  diaryDefaultView: 'CALENDAR',
};

export function useSettings() {
  return useQuery({
    queryKey: ['settings'],
    queryFn: async () => {
      const res = await api.get<ApiResponse<PersonSetting>>('/me/settings');
      return res.data.data ?? DEFAULT_SETTINGS;
    },
  });
}

/** PUT은 설정 전체를 보낸다(부분 업데이트 아님) — 현재 캐시값을 베이스로 변경분만 덮어써 전송. */
export function useUpdateSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (patch: Partial<PersonSetting>) => {
      const current = qc.getQueryData<PersonSetting>(['settings']) ?? DEFAULT_SETTINGS;
      const body: PersonSetting = { ...current, ...patch };
      const res = await api.put<ApiResponse<PersonSetting>>('/me/settings', body);
      if (!res.data.data) throw new Error(res.data.error?.message ?? '설정 저장에 실패했습니다.');
      return res.data.data;
    },
    onSuccess: (data) => {
      qc.setQueryData(['settings'], data);
      qc.invalidateQueries({ queryKey: ['schedules'] });
    },
  });
}
