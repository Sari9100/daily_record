import { Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Slot, Tabs, usePathname, useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';

type NavItem = { route: string; label: string; icon: keyof typeof Ionicons.glyphMap };

const NAV: NavItem[] = [
  { route: '/', label: '타임라인', icon: 'home-outline' },
  { route: '/ledger', label: '가계부', icon: 'wallet-outline' },
  { route: '/schedule', label: '일정', icon: 'calendar-outline' },
  { route: '/diary', label: '기록', icon: 'image-outline' },
  { route: '/more', label: '더보기', icon: 'ellipsis-horizontal' },
];

/** 웹: 좌측 사이드바 + 콘텐츠(Slot). 모바일: 하단 탭(docs/07 §1-2 플랫폼 분기). */
function WebSidebarLayout() {
  const pathname = usePathname();
  const router = useRouter();
  return (
    <View style={styles.webRow}>
      <View style={styles.sidebar}>
        <Text style={styles.brand}>Family OS</Text>
        {NAV.map((item) => {
          const active = pathname === item.route;
          return (
            <Pressable
              key={item.route}
              style={[styles.navItem, active && styles.navItemActive]}
              onPress={() => router.push(item.route as never)}>
              <Ionicons name={item.icon} size={20} color={active ? '#1a73e8' : '#5f6368'} />
              <Text style={[styles.navLabel, active && styles.navLabelActive]}>{item.label}</Text>
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
  if (Platform.OS === 'web') {
    return <WebSidebarLayout />;
  }
  return (
    <Tabs screenOptions={{ tabBarActiveTintColor: '#1a73e8', headerTitleStyle: { fontWeight: '700' } }}>
      <Tabs.Screen name="index" options={{ title: '타임라인', tabBarIcon: ({ color, size }) => <Ionicons name="home-outline" color={color} size={size} /> }} />
      <Tabs.Screen name="ledger" options={{ title: '가계부', tabBarIcon: ({ color, size }) => <Ionicons name="wallet-outline" color={color} size={size} /> }} />
      <Tabs.Screen name="schedule" options={{ title: '일정', tabBarIcon: ({ color, size }) => <Ionicons name="calendar-outline" color={color} size={size} /> }} />
      <Tabs.Screen name="diary" options={{ title: '기록', tabBarIcon: ({ color, size }) => <Ionicons name="image-outline" color={color} size={size} /> }} />
      <Tabs.Screen name="more" options={{ title: '더보기', tabBarIcon: ({ color, size }) => <Ionicons name="ellipsis-horizontal" color={color} size={size} /> }} />
    </Tabs>
  );
}

const styles = StyleSheet.create({
  webRow: { flex: 1, flexDirection: 'row', backgroundColor: '#fff' },
  sidebar: { width: 220, borderRightWidth: 1, borderRightColor: '#eceff1', paddingVertical: 16, paddingHorizontal: 12, gap: 4 },
  brand: { fontSize: 18, fontWeight: '700', color: '#1a73e8', paddingHorizontal: 12, paddingVertical: 12 },
  navItem: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 12, paddingVertical: 12, borderRadius: 10 },
  navItemActive: { backgroundColor: '#e8f0fe' },
  navLabel: { fontSize: 15, color: '#5f6368', fontWeight: '500' },
  navLabelActive: { color: '#1a73e8', fontWeight: '700' },
  content: { flex: 1 },
});
