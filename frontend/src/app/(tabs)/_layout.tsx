import { Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Slot, Tabs, usePathname, useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';

import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type NavItem = { route: string; label: string; icon: keyof typeof Ionicons.glyphMap };

const NAV: NavItem[] = [
  { route: '/schedule', label: '일정', icon: 'calendar-outline' },
  { route: '/ledger', label: '가계부', icon: 'wallet-outline' },
  { route: '/diary', label: '기록', icon: 'image-outline' },
  { route: '/', label: '타임라인', icon: 'home-outline' },
  { route: '/more', label: '더보기', icon: 'ellipsis-horizontal' },
];

/** 웹: 좌측 사이드바 + 콘텐츠(Slot). 모바일: 하단 탭(docs/07 §1-2 플랫폼 분기). */
function WebSidebarLayout() {
  const theme = useTheme();
  const pathname = usePathname();
  const router = useRouter();
  return (
    <View style={[styles.webRow, { backgroundColor: theme.background }]}>
      <View style={[styles.sidebar, { borderRightColor: theme.border }]}>
        <Text style={[styles.brand, { color: theme.primary }]}>Family OS</Text>
        {NAV.map((item) => {
          const active = pathname === item.route;
          return (
            <Pressable
              key={item.route}
              style={[styles.navItem, active && { backgroundColor: theme.primarySurface }]}
              onPress={() => router.push(item.route as never)}>
              <Ionicons name={item.icon} size={20} color={active ? theme.primary : theme.textSecondary} />
              <Text style={[styles.navLabel, { color: active ? theme.primary : theme.textSecondary, fontWeight: active ? '700' : '500' }]}>
                {item.label}
              </Text>
            </Pressable>
          );
        })}
      </View>
      <View style={styles.content}>
        <Slot />
      </View>
    </View>
  );
}

export default function TabsLayout() {
  const theme = useTheme();
  if (Platform.OS === 'web') {
    return <WebSidebarLayout />;
  }
  return (
    <Tabs screenOptions={{ tabBarActiveTintColor: theme.primary, headerTitleStyle: { fontWeight: '700' } }}>
      <Tabs.Screen name="schedule" options={{ title: '일정', tabBarIcon: ({ color, size }) => <Ionicons name="calendar-outline" color={color} size={size} /> }} />
      <Tabs.Screen name="ledger" options={{ title: '가계부', tabBarIcon: ({ color, size }) => <Ionicons name="wallet-outline" color={color} size={size} /> }} />
      <Tabs.Screen name="diary" options={{ title: '기록', tabBarIcon: ({ color, size }) => <Ionicons name="image-outline" color={color} size={size} /> }} />
      <Tabs.Screen name="index" options={{ title: '타임라인', tabBarIcon: ({ color, size }) => <Ionicons name="home-outline" color={color} size={size} /> }} />
      <Tabs.Screen name="more" options={{ title: '더보기', tabBarIcon: ({ color, size }) => <Ionicons name="ellipsis-horizontal" color={color} size={size} /> }} />
    </Tabs>
  );
}

const styles = StyleSheet.create({
  webRow: { flex: 1, flexDirection: 'row' },
  sidebar: { width: 220, borderRightWidth: 1, paddingVertical: Spacing.three, paddingHorizontal: Spacing.two + 4, gap: 4 },
  brand: { fontSize: 18, fontWeight: '700', paddingHorizontal: Spacing.two + 4, paddingVertical: Spacing.two + 4 },
  navItem: { flexDirection: 'row', alignItems: 'center', gap: Spacing.three, paddingHorizontal: Spacing.two + 4, paddingVertical: Spacing.two + 4, borderRadius: Radius.md },
  navLabel: { fontSize: 15 },
  content: { flex: 1 },
});
