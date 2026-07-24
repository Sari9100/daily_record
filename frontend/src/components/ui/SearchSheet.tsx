import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { BottomSheetModal } from './BottomSheetModal';
import { TextField } from './TextField';

export type SearchResultItem = { id: string; title: string; subtitle: string; date: string };

/**
 * 전체기간 검색 바텀시트 — 일정/기록 화면 공통. 입력 300ms 디바운스 후 `onQueryChange`로
 * 실제 서버 검색을 트리거하고(화면이 useSchedules/useDiaries({q}) 등으로 결과를 만들어 넘김),
 * 결과 선택 시 `onSelect`로 화면에 위임(캘린더를 그 날짜로 이동).
 */
export function SearchSheet({
  visible,
  onClose,
  onQueryChange,
  results,
  isLoading,
  onSelect,
  placeholder = '제목·메모·태그 검색',
}: {
  visible: boolean;
  onClose: () => void;
  onQueryChange: (q: string) => void;
  results: SearchResultItem[];
  isLoading: boolean;
  onSelect: (item: SearchResultItem) => void;
  placeholder?: string;
}) {
  const theme = useTheme();
  const [text, setText] = useState('');

  useEffect(() => {
    if (!visible) {
      setText('');
      onQueryChange('');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible]);

  useEffect(() => {
    const t = setTimeout(() => onQueryChange(text.trim()), 300);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [text]);

  return (
    <BottomSheetModal visible={visible} onClose={onClose} title="검색">
      <TextField label={placeholder} value={text} onChangeText={setText} placeholder={placeholder} autoFocus />
      <ScrollView style={styles.list} keyboardShouldPersistTaps="handled">
        {isLoading ? (
          <View style={styles.center}>
            <ActivityIndicator />
          </View>
        ) : text.trim().length === 0 ? (
          <Text style={[styles.hint, { color: theme.textMuted }]}>검색어를 입력하세요.</Text>
        ) : results.length === 0 ? (
          <Text style={[styles.hint, { color: theme.textMuted }]}>검색 결과가 없어요.</Text>
        ) : (
          results.map((r) => (
            <Pressable key={r.id} style={styles.row} onPress={() => onSelect(r)}>
              <Text style={[styles.rowTitle, { color: theme.text }]} numberOfLines={1}>
                {r.title}
              </Text>
              <Text style={[styles.rowSub, { color: theme.textMuted }]} numberOfLines={1}>
                {r.date} {r.subtitle ? `· ${r.subtitle}` : ''}
              </Text>
            </Pressable>
          ))
        )}
      </ScrollView>
    </BottomSheetModal>
  );
}

const styles = StyleSheet.create({
  list: { maxHeight: 400, marginTop: Spacing.two },
  center: { paddingVertical: Spacing.four, alignItems: 'center' },
  hint: { fontSize: 14, paddingVertical: Spacing.three, textAlign: 'center' },
  row: { paddingVertical: 10, gap: 2 },
  rowTitle: { fontSize: 15, fontWeight: '600' },
  rowSub: { fontSize: 12 },
});
