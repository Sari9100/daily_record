import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { API_BASE_URL } from '@/domain/config';
import { useAuthStore } from '@/store/auth';
import { clearRefreshToken, saveRefreshToken } from '@/lib/storage';
import type { ApiResponse, TokenResponse } from '@/domain/types';

/** 공유 axios 인스턴스. 요청에 Bearer 자동 부착 + 401 시 refresh 1회 큐잉(단일 비행) 후 재시도. */
export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ---- 401 → refresh 단일 비행 ----

let refreshing: Promise<string | null> | null = null;

/**
 * refresh token 으로 access token 재발급(회전). 동시 401 이 와도 refresh 는 1회만 실행되도록 공유.
 * 인터셉터 재귀를 피하려고 bare axios 로 호출한다. 실패 시 null → 호출부에서 로그아웃.
 */
export function refreshAccessToken(): Promise<string | null> {
  if (!refreshing) {
    refreshing = runRefresh().finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

async function runRefresh(): Promise<string | null> {
  const refreshToken = useAuthStore.getState().refreshToken;
  if (!refreshToken) {
    return null;
  }
  try {
    const res = await axios.post<ApiResponse<TokenResponse>>(
      `${API_BASE_URL}/auth/refresh`,
      { refreshToken },
      { headers: { 'Content-Type': 'application/json' } },
    );
    const data = res.data.data;
    if (!data) {
      return null;
    }
    useAuthStore.getState().setTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken });
    await saveRefreshToken(data.refreshToken);
    return data.accessToken;
  } catch {
    return null;
  }
}

function isAuthEndpoint(url?: string): boolean {
  return !!url && (url.includes('/auth/login') || url.includes('/auth/refresh'));
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
    const status = error.response?.status;

    if (status === 401 && original && !original._retry && !isAuthEndpoint(original.url)) {
      original._retry = true;
      const newToken = await refreshAccessToken();
      if (newToken) {
        original.headers.Authorization = `Bearer ${newToken}`;
        return api(original);
      }
      // refresh 실패 → 세션 종료
      await clearRefreshToken();
      useAuthStore.getState().clear();
    }
    return Promise.reject(error);
  },
);
