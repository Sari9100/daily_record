import { Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';

import { useAuthStore } from '@/store/auth';
import { signOut } from '@/api/session';

export default function MoreScreen() {
  const me = useAuthStore((s) => s.me);
  const router = useRouter();

  return (
    <SafeAreaView style={styles.safe} edges={['bottom']}>
      <View style={styles.section}>
        <Text style={styles.name}>{me?.name ?? '사용자'}</Text>
        <Text style={styles.sub}>
          {me?.familyName ? `${me.familyName} · ` : ''}
          {me?.role === 'PARENT' ? '부모' : me?.role === 'CHILD' ? '자녀' : ''}
        </Text>
      </View>

      <View style={styles.menu}>
        <MenuLink label="이벤트 묶음" onPress={() => router.push('/collections')} />
        <MenuLink label="가족·구성원" onPress={() => router.push('/family')} />
        <MenuLink label="외부연동 (구글 캘린더)" onPress={() => router.push('/integrations')} />
        <MenuLink label="개인 설정" onPress={() => router.push('/settings')} />
      </View>

      <Pressable style={styles.logout} onPress={() => signOut()}>
        <Text style={styles.logoutText}>로그아웃</Text>
      </Pressable>
    </SafeAreaView>
  );
}

function MenuLink({ label, onPress }: { label: string; onPress: () => void }) {
  return (
    <Pressable style={styles.menuItem} onPress={onPress}>
      <Text style={styles.menuItemText}>{label}</Text>
      <Ionicons name="chevron-forward" size={18} color="#9aa0a6" />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#fff', padding: 20, gap: 24 },
  section: { gap: 4, paddingVertical: 8 },
  name: { fontSize: 22, fontWeight: '700', color: '#202124' },
  sub: { fontSize: 14, color: '#5f6368' },
  menu: { gap: 4 },
  menuItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: '#f1f3f4',
  },
  menuItemText: { fontSize: 16, color: '#202124' },
  menuItemDisabled: { fontSize: 16, color: '#9aa0a6', paddingVertical: 14 },
  logout: { marginTop: 'auto', borderWidth: 1, borderColor: '#d93025', borderRadius: 10, paddingVertical: 14, alignItems: 'center' },
  logoutText: { color: '#d93025', fontSize: 16, fontWeight: '600' },
});
