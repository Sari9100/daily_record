import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type { ApiResponse } from '@/domain/types';

export type GoogleSyncResult = { fullSync: boolean; created: number; updated: number; deleted: number };
export type GooglePushResult = { inserted: number; updated: number; deleted: number };

export function useGoogleConnect() {
  return useMutation({
    mutationFn: async () => {
      const res = await api.post<ApiResponse<{ authorizationUrl: string }>>('/integrations/google/connect');
      if (!res.data.data) throw new Error(res.data.error?.message ?? '구글 연결 URL 발급에 실패했습니다.');
      return res.data.data.authorizationUrl;
    },
  });
}

export function useGoogleSync() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      const res = await api.post<ApiResponse<GoogleSyncResult>>('/integrations/google/sync');
      if (!res.data.data) throw new Error(res.data.error?.message ?? '동기화에 실패했습니다.');
      return res.data.data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['schedules'] });
      qc.invalidateQueries({ queryKey: ['timeline'] });
    },
  });
}

export function useGooglePush() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      const res = await api.post<ApiResponse<GooglePushResult>>('/integrations/google/push');
      if (!res.data.data) throw new Error(res.data.error?.message ?? '전송에 실패했습니다.');
      return res.data.data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['schedules'] }),
  });
}

export function useGoogleDisconnect() {
  return useMutation({
    mutationFn: () => api.delete('/integrations/google').then(() => undefined),
  });
}
