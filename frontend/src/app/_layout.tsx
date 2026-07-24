import { useEffect } from 'react';
import { ActivityIndicator, View } from 'react-native';
import { Stack, useRouter, useSegments } from 'expo-router';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { QueryClientProvider } from '@tanstack/react-query';
import { StatusBar } from 'expo-status-bar';

import { queryClient } from '@/lib/query';
import { useAuthStore } from '@/store/auth';
import { bootstrapSession } from '@/api/session';

/** 인증 상태에 따라 로그인/메인을 가르는 게이트. */
function AuthGate() {
  const status = useAuthStore((s) => s.status);
  const segments = useSegments();
  const router = useRouter();

  useEffect(() => {
    void bootstrapSession();
  }, []);

  useEffect(() => {
    if (status === 'loading') {
      return;
    }
    const inLogin = segments[0] === 'login';
    if (status === 'authenticated' && inLogin) {
      router.replace('/');
    } else if (status === 'unauthenticated' && !inLogin) {
      router.replace('/login');
    }
  }, [status, segments, router]);

  if (status === 'loading') {
    return (
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator />
      </View>
    );
  }
  return (
    <Stack screenOptions={{ headerShown: false }}>
      <Stack.Screen
        name="transaction/new"
        options={{ presentation: 'transparentModal', animation: 'slide_from_right', headerShown: false }}
      />
      <Stack.Screen
        name="transaction/[id]"
        options={{ presentation: 'transparentModal', animation: 'slide_from_right', headerShown: false }}
      />
      <Stack.Screen name="accounts" options={{ headerShown: true, title: '계좌 관리' }} />
      <Stack.Screen name="categories" options={{ headerShown: true, title: '카테고리 관리' }} />
      <Stack.Screen name="statistics" options={{ headerShown: true, title: '통계' }} />
      <Stack.Screen
        name="schedule/new"
        options={{ presentation: 'transparentModal', animation: 'slide_from_right', headerShown: false }}
      />
      <Stack.Screen
        name="schedule/[id]"
        options={{ presentation: 'transparentModal', animation: 'slide_from_right', headerShown: false }}
      />
      <Stack.Screen
        name="diary/new"
        options={{ presentation: 'modal', headerShown: true, title: '기록 작성' }}
      />
      <Stack.Screen
        name="diary/[id]"
        options={{ presentation: 'modal', headerShown: true, title: '기록 편집' }}
      />
      <Stack.Screen name="collections" options={{ headerShown: true, title: '이벤트 묶음' }} />
      <Stack.Screen name="collection/[id]" options={{ headerShown: true, title: '묶음 요약' }} />
      <Stack.Screen name="family" options={{ headerShown: true, title: '가족·구성원' }} />
      <Stack.Screen name="integrations" options={{ headerShown: true, title: '외부연동' }} />
      <Stack.Screen name="settings" options={{ headerShown: true, title: '개인 설정' }} />
      <Stack.Screen name="change-password" options={{ headerShown: true, title: '비밀번호 변경' }} />
    </Stack>
  );
}

export default function RootLayout() {
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <QueryClientProvider client={queryClient}>
        <SafeAreaProvider>
          <StatusBar style="auto" />
          <AuthGate />
        </SafeAreaProvider>
      </QueryClientProvider>
    </GestureHandlerRootView>
  );
}
