import { StyleSheet, Text, View } from 'react-native';

import { Chip } from '@/components/ui/Chip';
import { useTheme } from '@/hooks/use-theme';

export type ChipOption<T extends string | number> = { value: T; label: string };

/** 단일 선택 칩 그룹 (유형·계좌·카테고리·공개범위 선택용). */
export function ChipGroup<T extends string | number>({
  options,
  value,
  onChange,
  emptyText,
}: {
  options: ChipOption<T>[];
  value: T | null | undefined;
  onChange: (value: T) => void;
  emptyText?: string;
}) {
  const theme = useTheme();
  if (options.length === 0) {
    return <Text style={[styles.empty, { color: theme.textMuted }]}>{emptyText ?? '선택지가 없습니다.'}</Text>;
  }
  return (
    <View style={styles.row}>
      {options.map((opt) => (
        <Chip key={String(opt.value)} label={opt.label} selected={opt.value === value} onPress={() => onChange(opt.value)} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  empty: { fontSize: 13 },
});
