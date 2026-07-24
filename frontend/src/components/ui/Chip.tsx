import { Pressable, StyleSheet, Text } from 'react-native';

import { Radius } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** 단일 선택형 칩. `components/ChipGroup.tsx`가 이 컴포넌트를 여러 개 배치한다. */
export function Chip({ label, selected, onPress }: { label: string; selected: boolean; onPress: () => void }) {
  const theme = useTheme();
  return (
    <Pressable
      onPress={onPress}
      style={[
        styles.chip,
        {
          borderColor: selected ? theme.primary : theme.border,
          backgroundColor: selected ? theme.primary : theme.background,
        },
      ]}>
      <Text style={[styles.label, { color: selected ? '#fff' : theme.textSecondary, fontWeight: selected ? '600' : '400' }]}>
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  chip: { paddingHorizontal: 14, paddingVertical: 9, borderRadius: Radius.pill, borderWidth: 1 },
  label: { fontSize: 14 },
});
