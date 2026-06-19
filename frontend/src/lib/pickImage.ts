import { Platform } from 'react-native';

/**
 * 웹 파일 선택기로 이미지 1장 고르기. 네이티브는 추후 expo-image-picker 로 분기(현재 null).
 */
export function pickImageWeb(): Promise<File | null> {
  if (Platform.OS !== 'web' || typeof document === 'undefined') {
    return Promise.resolve(null);
  }
  return new Promise((resolve) => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*';
    input.onchange = () => resolve(input.files && input.files[0] ? input.files[0] : null);
    input.click();
  });
}
