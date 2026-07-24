import { type ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** 제목+부제+trailing(아이콘/버튼) 행. 계좌·카테고리·가족·가계부 목록에서 공통 사용. */
export function ListRow({
  title,
  subtitle,
  trailing,
  onPress,
  disabled,
}: {
  title: string;
  subtitle?: string;
  trailing?: ReactNode;
  onPress?: () => void;
  disabled?: boolean;
}) {
  const theme = useTheme();
  const content = (
    <View style={styles.row}>
      <View style={styles.left}>
        <Text style={[styles.title, { color: theme.text }]} numberOfLines={1}>
          {title}
        </Text>
        {subtitle ? (
          <Text style={[styles.subtitle, { color: theme.textSecondary }]} numberOfLines={1}>
            {subtitle}
          </Text>
        ) : null}
      </View>
      {trailing}
    </View>
  );
  if (!onPress) return content;
  return (
    <Pressable style={disabled ? styles.disabled : undefined} onPress={onPress} disabled={disabled}>
      {content}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', paddingVertical: 14, gap: Spacing.three },
  left: { flex: 1, gap: 3 },
  title: { fontSize: 16, fontWeight: '500' },
  subtitle: { fontSize: 13 },
  disabled: { opacity: 1 },
});
