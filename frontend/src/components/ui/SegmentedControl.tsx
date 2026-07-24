import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Radius } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type SegmentOption<T extends string> = { value: T; label: string };

/** 2~3개 옵션 중 하나를 즉시 전환하는 스위치형 컨트롤 (인라인/캘린더 뷰 전환 등). */
export function SegmentedControl<T extends string>({
  options,
  value,
  onChange,
}: {
  options: SegmentOption<T>[];
  value: T;
  onChange: (value: T) => void;
}) {
  const theme = useTheme();
  return (
    <View style={[styles.track, { backgroundColor: theme.backgroundElement }]}>
      {options.map((opt) => {
        const selected = opt.value === value;
        return (
          <Pressable
            key={opt.value}
            onPress={() => onChange(opt.value)}
            style={[styles.segment, selected && { backgroundColor: theme.background }]}>
            <Text style={[styles.label, { color: selected ? theme.text : theme.textSecondary, fontWeight: selected ? '700' : '500' }]}>
              {opt.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  track: { flexDirection: 'row', borderRadius: Radius.md, padding: 3, gap: 2 },
  segment: { flex: 1, paddingVertical: 8, borderRadius: Radius.sm, alignItems: 'center' },
  label: { fontSize: 14 },
});
