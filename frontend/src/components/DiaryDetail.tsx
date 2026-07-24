import { Image, ScrollView, StyleSheet, Text, View } from 'react-native';

import { Button } from '@/components/ui';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { mediaUrl } from '@/domain/config';
import type { Diary } from '@/domain/diary';

const VISIBILITY_LABEL: Record<Diary['visibility'], string> = {
  PRIVATE: '개인',
  SHARED_PERSONAL: '공유개인',
  PARENTS: '부모공유',
  FAMILY: '가족',
};

/** 기록 읽기 전용 보기 — 기록 목록에서 카드를 탭하면 먼저 이 화면이 뜨고, "수정" 버튼으로 편집 화면(DiaryForm)으로 전환. */
export function DiaryDetail({
  diary,
  onEdit,
  onDelete,
  deleting,
}: {
  diary: Diary;
  onEdit: () => void;
  onDelete: () => void;
  deleting?: boolean;
}) {
  const theme = useTheme();

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <View style={styles.headRow}>
        <Text style={[styles.date, { color: theme.textSecondary }]}>{diary.recordedOn}</Text>
        <View style={[styles.badge, { backgroundColor: theme.primarySurface }]}>
          <Text style={[styles.badgeText, { color: theme.primary }]}>{VISIBILITY_LABEL[diary.visibility]}</Text>
        </View>
      </View>

      {diary.title ? <Text style={[styles.title, { color: theme.text }]}>{diary.title}</Text> : null}

      <Text style={[styles.content, { color: theme.text }]}>{diary.content}</Text>

      {diary.photos.length > 0 && (
        <View style={styles.photoGrid}>
          {diary.photos.map((p) =>
            p.url ? (
              <Image key={p.id} source={{ uri: mediaUrl(p.url) }} style={[styles.photo, { backgroundColor: theme.surfaceMuted }]} resizeMode="cover" />
            ) : null,
          )}
        </View>
      )}

      {diary.tags.length > 0 && (
        <View style={styles.tagRow}>
          {diary.tags.map((t) => (
            <Text key={t} style={[styles.tag, { color: theme.textSecondary, borderColor: theme.border }]}>
              {t}
            </Text>
          ))}
        </View>
      )}

      <View style={styles.actions}>
        <Button label="수정" onPress={onEdit} style={styles.flexBtn} />
        <Button label="삭제" variant="danger" onPress={onDelete} loading={deleting} disabled={deleting} style={styles.flexBtn} />
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.three },
  headRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  date: { fontSize: 13 },
  badge: { paddingHorizontal: 10, paddingVertical: 4, borderRadius: Radius.pill },
  badgeText: { fontSize: 12, fontWeight: '600' },
  title: { fontSize: 22, fontWeight: '700' },
  content: { fontSize: 16, lineHeight: 24 },
  photoGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two },
  photo: { width: 110, height: 110, borderRadius: Radius.sm },
  tagRow: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two },
  tag: { fontSize: 12, borderWidth: 1, borderRadius: Radius.pill, paddingHorizontal: 10, paddingVertical: 4 },
  actions: { flexDirection: 'row', gap: Spacing.three, marginTop: Spacing.two },
  flexBtn: { flex: 1 },
});
