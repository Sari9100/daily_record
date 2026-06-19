import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';

/**
 * 토큰 저장소 (docs/06, frontend/CLAUDE.md 0).
 * - 네이티브: expo-secure-store (refresh token 만 영속 — access 는 메모리/store).
 * - 웹: 메모리 (MVP — 새로고침 시 재로그인). XSS 토큰 탈취 방지가 이유.
 */

const REFRESH_KEY = 'familyos.refreshToken';
const memory: Record<string, string> = {};

export async function saveRefreshToken(token: string): Promise<void> {
  if (Platform.OS === 'web') {
    memory[REFRESH_KEY] = token;
    return;
  }
  await SecureStore.setItemAsync(REFRESH_KEY, token);
}

export async function loadRefreshToken(): Promise<string | null> {
  if (Platform.OS === 'web') {
    return memory[REFRESH_KEY] ?? null;
  }
  return (await SecureStore.getItemAsync(REFRESH_KEY)) ?? null;
}

export async function clearRefreshToken(): Promise<void> {
  if (Platform.OS === 'web') {
    delete memory[REFRESH_KEY];
    return;
  }
  await SecureStore.deleteItemAsync(REFRESH_KEY);
}
