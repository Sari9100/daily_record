import { Pressable, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

import { Radius } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** 인라인↔캘린더 전환용 소형 아이콘 버튼. 자주 안 쓰는 기능이라 SegmentedControl보다 눈에 덜 띄게. */
export function ViewModeToggle({
  mode,
  onChange,
}: {
  mode: 'INLINE' | 'CALENDAR';
  onChange: (mode: 'INLINE' | 'CALENDAR') => void;
}) {
  const theme = useTheme();
  return (
    <Pressable
      hitSlop={6}
      onPress={() => onChange(mode === 'INLINE' ? 'CALENDAR' : 'INLINE')}
      style={[styles.btn, { backgroundColor: theme.backgroundElement }]}>
      <Ionicons name={mode === 'INLINE' ? 'calendar-outline' : 'list-outline'} size={16} color={theme.textSecondary} />
    </Pressable>
  );
}

/** 인라인 모드 전용 날짜 오름차순/내림차순 소형 토글. */
export function SortOrderToggle({
  order,
  onChange,
}: {
  order: 'asc' | 'desc';
  onChange: (order: 'asc' | 'desc') => void;
}) {
  const theme = useTheme();
  return (
    <Pressable
      hitSlop={6}
      onPress={() => onChange(order === 'asc' ? 'desc' : 'asc')}
      style={[styles.btn, { backgroundColor: theme.backgroundElement }]}>
      <Ionicons name={order === 'asc' ? 'arrow-up' : 'arrow-down'} size={16} color={theme.textSecondary} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  btn: { width: 28, height: 28, borderRadius: Radius.sm, alignItems: 'center', justifyContent: 'center' },
});
