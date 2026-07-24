import { type ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { addMonths, format, subMonths } from 'date-fns';

import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** 월 이동 컨트롤. 인라인/캘린더 두 뷰 모두에서 조회 월을 바꾸는 공통 진입점. `right` — 뷰모드/정렬 같은 소형 토글 자리. */
export function MonthNav({
  month,
  onMonthChange,
  right,
}: {
  month: Date;
  onMonthChange: (month: Date) => void;
  right?: ReactNode;
}) {
  const theme = useTheme();
  return (
    <View style={styles.nav}>
      <View style={styles.spacer} />
      <View style={styles.center}>
        <Pressable hitSlop={8} onPress={() => onMonthChange(subMonths(month, 1))}>
          <Ionicons name="chevron-back" size={22} color={theme.text} />
        </Pressable>
        <Text style={[styles.label, { color: theme.text }]}>{format(month, 'yyyy년 M월')}</Text>
        <Pressable hitSlop={8} onPress={() => onMonthChange(addMonths(month, 1))}>
          <Ionicons name="chevron-forward" size={22} color={theme.text} />
        </Pressable>
      </View>
      <View style={[styles.spacer, styles.right]}>{right}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  nav: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.two,
  },
  spacer: { minWidth: 60 },
  right: { flexDirection: 'row', justifyContent: 'flex-end', gap: Spacing.two },
  center: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  label: { fontSize: 16, fontWeight: '700' },
});
