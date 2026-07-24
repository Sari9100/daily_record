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

/** 플랫폼 공통 알림. 웹은 window.alert, 네이티브는 Alert. 닫힘까지 대기(연속 동작 순서 보장용). */
export function alertAsync(message: string, title = '알림'): Promise<void> {
  if (Platform.OS === 'web') {
    if (typeof window !== 'undefined') window.alert(message);
    return Promise.resolve();
  }
  return new Promise((resolve) => {
    Alert.alert(title, message, [{ text: '확인', onPress: () => resolve() }]);
  });
}
