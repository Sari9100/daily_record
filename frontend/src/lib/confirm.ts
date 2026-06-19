import { Alert, Platform } from 'react-native';

/** 플랫폼 공통 확인 다이얼로그. 웹은 window.confirm, 네이티브는 Alert. */
export function confirmAsync(message: string, confirmLabel = '삭제'): Promise<boolean> {
  if (Platform.OS === 'web') {
    return Promise.resolve(typeof window !== 'undefined' ? window.confirm(message) : true);
  }
  return new Promise((resolve) => {
    Alert.alert('확인', message, [
      { text: '취소', style: 'cancel', onPress: () => resolve(false) },
      { text: confirmLabel, style: 'destructive', onPress: () => resolve(true) },
    ]);
  });
}
