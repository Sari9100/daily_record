import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type ScreenHeaderLink = { label: string; onPress: () => void };

/** 화면 제목 + 우측 링크들(가계부의 통계/계좌/카테고리 등). */
export function ScreenHeader({ title, links }: { title: string; links?: ScreenHeaderLink[] }) {
  const theme = useTheme();
  return (
    <View style={styles.header}>
      <Text style={[styles.title, { color: theme.text }]}>{title}</Text>
      {links && links.length > 0 && (
        <View style={styles.links}>
          {links.map((l) => (
            <Pressable key={l.label} onPress={l.onPress} hitSlop={8}>
              <Text style={[styles.link, { color: theme.primary }]}>{l.label}</Text>
            </Pressable>
          ))}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two + 4,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  title: { fontSize: 22, fontWeight: '700' },
  links: { flexDirection: 'row', gap: Spacing.three },
  link: { fontSize: 15, fontWeight: '600' },
});
