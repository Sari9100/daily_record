import { api } from './client';
import type { ApiResponse, LoginResponse, MeResponse } from '@/domain/types';

export async function loginRequest(loginId: string, password: string): Promise<LoginResponse> {
  const res = await api.post<ApiResponse<LoginResponse>>('/auth/login', { loginId, password });
  if (!res.data.data) {
    throw new Error(res.data.error?.message ?? '로그인에 실패했습니다.');
  }
  return res.data.data;
}

export async function fetchMe(): Promise<MeResponse> {
  const res = await api.get<ApiResponse<MeResponse>>('/auth/me');
  if (!res.data.data) {
    throw new Error(res.data.error?.message ?? '사용자 정보를 불러오지 못했습니다.');
  }
  return res.data.data;
}

export async function logoutRequest(refreshToken: string): Promise<void> {
  await api.post('/auth/logout', { refreshToken });
}
