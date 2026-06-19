import { Pressable, StyleSheet, Text, View } from 'react-native';

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
  if (options.length === 0) {
    return <Text style={styles.empty}>{emptyText ?? '선택지가 없습니다.'}</Text>;
  }
  return (
    <View style={styles.row}>
      {options.map((opt) => {
        const selected = opt.value === value;
        return (
          <Pressable
            key={String(opt.value)}
            onPress={() => onChange(opt.value)}
            style={[styles.chip, selected && styles.chipSelected]}>
            <Text style={[styles.chipText, selected && styles.chipTextSelected]}>{opt.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: '#dadce0',
    backgroundColor: '#fff',
  },
  chipSelected: { backgroundColor: '#1a73e8', borderColor: '#1a73e8' },
  chipText: { fontSize: 14, color: '#3c4043' },
  chipTextSelected: { color: '#fff', fontWeight: '600' },
  empty: { fontSize: 13, color: '#9aa0a6' },
});
