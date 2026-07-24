import { StyleSheet, Text, View } from 'react-native';

import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** 목록 화면 공통 빈 상태 플레이스홀더. */
export function EmptyState({ text }: { text: string }) {
  const theme = useTheme();
  return (
    <View style={styles.box}>
      <Text style={[styles.text, { color: theme.textSecondary }]}>{text}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  box: { flexGrow: 1, alignItems: 'center', justifyContent: 'center', padding: Spacing.four },
  text: { textAlign: 'center', fontSize: 15, lineHeight: 22 },
});
