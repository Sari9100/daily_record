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

/** 성공 시 이 계정의 모든 세션(현재 기기 포함)이 서버에서 폐기된다 — 호출부가 곧바로 로그아웃 처리해야 함. */
export async function changePasswordRequest(currentPassword: string, newPassword: string): Promise<void> {
  const res = await api.put<ApiResponse<void>>('/auth/password', { currentPassword, newPassword });
  if (!res.data.success) {
    throw new Error(res.data.error?.message ?? '비밀번호 변경에 실패했습니다.');
  }
}
