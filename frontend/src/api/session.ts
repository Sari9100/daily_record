import axios from 'axios';
import { API_BASE_URL } from '@/domain/config';
import { fetchMe, loginRequest, logoutRequest } from './auth';
import { useAuthStore } from '@/store/auth';
import { clearRefreshToken, loadRefreshToken, saveRefreshToken } from '@/lib/storage';
import type { ApiResponse, Me, TokenResponse } from '@/domain/types';

/** 로그인 → 토큰 저장 + 세션 확정. */
export async function signIn(loginId: string, password: string): Promise<void> {
  const res = await loginRequest(loginId, password);
  const me: Me = {
    personId: res.person.id,
    name: res.person.name,
    familyId: res.person.familyId,
    role: res.person.role,
  };
  useAuthStore.getState().setSession({
    accessToken: res.accessToken,
    refreshToken: res.refreshToken,
    me,
  });
  await saveRefreshToken(res.refreshToken);
}

/** 로그아웃 — 서버 refresh 폐기 시도 후 클라/저장소 초기화. */
export async function signOut(): Promise<void> {
  const refreshToken = useAuthStore.getState().refreshToken;
  if (refreshToken) {
    try {
      await logoutRequest(refreshToken);
    } catch {
      // 서버 폐기 실패해도 클라 세션은 끊는다
    }
  }
  await clearRefreshToken();
  useAuthStore.getState().clear();
}

/**
 * 앱 시작 시 세션 복구 — 저장된 refresh token 으로 access 재발급 + me 조회.
 * 웹은 메모리 저장이라 새로고침 시 토큰이 없어 unauthenticated 로 떨어진다(MVP).
 */
export async function bootstrapSession(): Promise<void> {
  const refreshToken = await loadRefreshToken();
  if (!refreshToken) {
    useAuthStore.getState().clear();
    return;
  }
  try {
    const res = await axios.post<ApiResponse<TokenResponse>>(
      `${API_BASE_URL}/auth/refresh`,
      { refreshToken },
      { headers: { 'Content-Type': 'application/json' } },
    );
    const tokens = res.data.data;
    if (!tokens) {
      throw new Error('refresh failed');
    }
    useAuthStore.getState().setTokens({
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
    });
    await saveRefreshToken(tokens.refreshToken);

    const meRes = await fetchMe();
    const me: Me = {
      personId: meRes.person.id,
      name: meRes.person.name,
      familyId: meRes.family.id,
      familyName: meRes.family.name,
      role: meRes.role,
    };
    useAuthStore.getState().setSession({
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      me,
    });
  } catch {
    await clearRefreshToken();
    useAuthStore.getState().clear();
  }
}
