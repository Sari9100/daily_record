import { useEffect } from 'react';
import { ActivityIndicator, View } from 'react-native';
import { Slot, useRouter, useSegments } from 'expo-router';
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
  return <Slot />;
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
