import { StyleSheet, Text, View } from 'react-native';

import { Radius } from '@/constants/theme';

/** 색상 뱃지 — 타임라인 유형, 일정 동기화 상태 등. 색상은 호출부가 토큰에서 골라 전달. */
export function Badge({ label, color, textColor = '#fff' }: { label: string; color: string; textColor?: string }) {
  return (
    <View style={[styles.badge, { backgroundColor: color }]}>
      <Text style={[styles.text, { color: textColor }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: Radius.sm, alignSelf: 'flex-start' },
  text: { fontSize: 11, fontWeight: '600' },
});
