import { Platform } from 'react-native';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type { ApiResponse } from '@/domain/types';
import type { DiaryPhoto } from '@/domain/diary';

type PresignResponse = {
  photoId: number;
  uploadUrl: string | null;
  storageKey: string;
  duplicated: boolean;
};

type PhotoResponse = {
  id: number;
  diaryId: number | null;
  url: string;
  width: number | null;
  height: number | null;
  takenAt: string | null;
  mimeType: string | null;
};

async function sha256Hex(buf: ArrayBuffer): Promise<string> {
  const digest = await globalThis.crypto.subtle.digest('SHA-256', buf);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

/** 사진 1장을 일기에 첨부 (웹). presign → 바이너리 PUT → complete. photoHash 중복 시 업로드 생략. */
export async function uploadPhotoToDiary(file: File, diaryId: number): Promise<DiaryPhoto> {
  if (Platform.OS !== 'web') {
    throw new Error('사진 업로드는 현재 웹에서만 지원됩니다(앱은 추후).');
  }
  const buf = await file.arrayBuffer();
  const hash = await sha256Hex(buf);

  const presignRes = await api.post<ApiResponse<PresignResponse>>('/photos/presign', {
    originalFilename: file.name || 'photo.jpg',
    mimeType: file.type || 'image/jpeg',
    fileSize: file.size,
    photoHash: hash,
    diaryId,
    takenAt: null,
  });
  const data = presignRes.data.data;
  if (!data) throw new Error(presignRes.data.error?.message ?? '사진 등록에 실패했습니다.');

  if (!data.duplicated) {
    await api.put(`/photos/${data.photoId}/binary`, buf, {
      headers: { 'Content-Type': 'application/octet-stream' },
      transformRequest: [(d) => d],
    });
  }
  const completeRes = await api.post<ApiResponse<PhotoResponse>>(`/photos/${data.photoId}/complete`, {
    width: null,
    height: null,
  });
  const p = completeRes.data.data;
  if (!p) throw new Error('사진 확정에 실패했습니다.');
  return { id: p.id, url: p.url, width: p.width, height: p.height, takenAt: p.takenAt };
}

export function useDeletePhoto() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.delete(`/photos/${id}`).then(() => undefined),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['diaries'] });
      qc.invalidateQueries({ queryKey: ['timeline'] });
    },
  });
}
