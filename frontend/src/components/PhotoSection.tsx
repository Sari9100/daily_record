import { useState } from 'react';
import { ActivityIndicator, Image, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { useQueryClient } from '@tanstack/react-query';
import { Ionicons } from '@expo/vector-icons';

import { uploadPhotoToDiary, useDeletePhoto } from '@/api/photo';
import { pickImageWeb } from '@/lib/pickImage';
import { confirmAsync } from '@/lib/confirm';
import { mediaUrl } from '@/domain/config';
import type { DiaryPhoto } from '@/domain/diary';

/** 일기 사진 첨부/표시 (편집 화면). 웹은 파일선택 업로드, 네이티브는 추후. */
export function PhotoSection({ diaryId, initialPhotos }: { diaryId: number; initialPhotos: DiaryPhoto[] }) {
  const [photos, setPhotos] = useState<DiaryPhoto[]>(initialPhotos);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const deleteMut = useDeletePhoto();
  const qc = useQueryClient();

  const add = async () => {
    const file = await pickImageWeb();
    if (!file) return;
    setUploading(true);
    setError(null);
    try {
      const photo = await uploadPhotoToDiary(file, diaryId);
      setPhotos((prev) => [...prev, photo]);
      qc.invalidateQueries({ queryKey: ['diaries'] });
      qc.invalidateQueries({ queryKey: ['timeline'] });
    } catch (e) {
      setError(e instanceof Error ? e.message : '업로드에 실패했습니다.');
    } finally {
      setUploading(false);
    }
  };

  const remove = async (id: number) => {
    if (!(await confirmAsync('사진을 삭제할까요?'))) return;
    await deleteMut.mutateAsync(id);
    setPhotos((prev) => prev.filter((p) => p.id !== id));
  };

  return (
    <View style={styles.section}>
      <Text style={styles.label}>사진</Text>
      <View style={styles.grid}>
        {photos.map((p) => (
          <View key={p.id} style={styles.thumbWrap}>
            {p.url ? (
              <Image source={{ uri: mediaUrl(p.url) }} style={styles.thumb} resizeMode="cover" />
            ) : (
              <View style={[styles.thumb, styles.thumbEmpty]} />
            )}
            <Pressable style={styles.removeBadge} hitSlop={6} onPress={() => remove(p.id)}>
              <Ionicons name="close" size={14} color="#fff" />
            </Pressable>
          </View>
        ))}

        {Platform.OS === 'web' ? (
          <Pressable style={styles.addBtn} onPress={add} disabled={uploading}>
            {uploading ? (
              <ActivityIndicator color="#1a73e8" />
            ) : (
              <Ionicons name="add" size={28} color="#1a73e8" />
            )}
          </Pressable>
        ) : (
          <Text style={styles.nativeNote}>사진 추가는 앱에서 추후 지원</Text>
        )}
      </View>
      {error && <Text style={styles.error}>{error}</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  section: { gap: 8 },
  label: { fontSize: 13, fontWeight: '600', color: '#3c4043' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  thumbWrap: { position: 'relative' },
  thumb: { width: 88, height: 88, borderRadius: 8, backgroundColor: '#f1f3f4' },
  thumbEmpty: { alignItems: 'center', justifyContent: 'center' },
  removeBadge: {
    position: 'absolute',
    top: -6,
    right: -6,
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: '#d93025',
    alignItems: 'center',
    justifyContent: 'center',
  },
  addBtn: {
    width: 88,
    height: 88,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#dadce0',
    borderStyle: 'dashed',
    alignItems: 'center',
    justifyContent: 'center',
  },
  nativeNote: { fontSize: 12, color: '#9aa0a6', alignSelf: 'center' },
  error: { color: '#d93025', fontSize: 13 },
});
