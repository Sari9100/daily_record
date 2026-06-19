import { create } from 'zustand';
import type { Me } from '@/domain/types';

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

type AuthState = {
  status: AuthStatus;
  accessToken: string | null;
  refreshToken: string | null;
  me: Me | null;

  /** 로그인/부트스트랩 성공 — 세션 확정. */
  setSession: (p: { accessToken: string; refreshToken: string; me: Me }) => void;
  /** 토큰 회전 후 갱신(세션 유지). */
  setTokens: (p: { accessToken: string; refreshToken: string }) => void;
  setRefreshToken: (token: string) => void;
  setStatus: (status: AuthStatus) => void;
  /** 로그아웃/세션 만료 — 클라 상태 초기화. */
  clear: () => void;
};

/**
 * 클라 인증 상태(Zustand). axios 인터셉터 등 React 외부에서는 useAuthStore.getState() 로 접근.
 * 토큰의 영속화는 lib/storage 가 담당(여기엔 런타임 값만).
 */
export const useAuthStore = create<AuthState>((set) => ({
  status: 'loading',
  accessToken: null,
  refreshToken: null,
  me: null,

  setSession: ({ accessToken, refreshToken, me }) =>
    set({ accessToken, refreshToken, me, status: 'authenticated' }),
  setTokens: ({ accessToken, refreshToken }) => set({ accessToken, refreshToken }),
  setRefreshToken: (refreshToken) => set({ refreshToken }),
  setStatus: (status) => set({ status }),
  clear: () => set({ accessToken: null, refreshToken: null, me: null, status: 'unauthenticated' }),
}));
