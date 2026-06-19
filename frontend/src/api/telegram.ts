import { useMutation } from '@tanstack/react-query';
import { api } from './client';
import type { ApiResponse } from '@/domain/types';

/** 본인 텔레그램 계정 연결 (telegram_user_id ↔ 로그인 Person). */
export function useTelegramLink() {
  return useMutation({
    mutationFn: async (telegramUserId: number) => {
      const res = await api.post<ApiResponse<void>>('/me/telegram', { telegramUserId });
      if (!res.data.success) throw new Error(res.data.error?.message ?? '텔레그램 연결에 실패했습니다.');
    },
  });
}

export function useTelegramUnlink() {
  return useMutation({
    mutationFn: () => api.delete('/me/telegram').then(() => undefined),
  });
}
