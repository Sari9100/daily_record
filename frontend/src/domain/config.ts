/**
 * API 베이스 URL. 기본은 로컬 백엔드. 실기기/배포 시 EXPO_PUBLIC_API_BASE_URL 로 주입.
 *
 * 주의: 네이티브 실기기에서는 localhost 가 기기 자신을 가리키므로, 같은 LAN 의 Mac IP 로 바꿔야 한다.
 * 예) EXPO_PUBLIC_API_BASE_URL=http://192.168.0.10:8080/api/v1
 */
export const API_BASE_URL =
  process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1';
