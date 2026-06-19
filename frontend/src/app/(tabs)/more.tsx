import { Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { useAuthStore } from '@/store/auth';
import { signOut } from '@/api/session';

export default function MoreScreen() {
  const me = useAuthStore((s) => s.me);

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
        <Text style={styles.menuItemDisabled}>이벤트 묶음 (다음 단계)</Text>
        <Text style={styles.menuItemDisabled}>가족·구성원 (다음 단계)</Text>
        <Text style={styles.menuItemDisabled}>외부연동(구글 캘린더) (다음 단계)</Text>
        <Text style={styles.menuItemDisabled}>개인 설정 (다음 단계)</Text>
      </View>

      <Pressable style={styles.logout} onPress={() => signOut()}>
        <Text style={styles.logoutText}>로그아웃</Text>
      </Pressable>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#fff', padding: 20, gap: 24 },
  section: { gap: 4, paddingVertical: 8 },
  name: { fontSize: 22, fontWeight: '700', color: '#202124' },
  sub: { fontSize: 14, color: '#5f6368' },
  menu: { gap: 16 },
  menuItemDisabled: { fontSize: 16, color: '#9aa0a6' },
  logout: { marginTop: 'auto', borderWidth: 1, borderColor: '#d93025', borderRadius: 10, paddingVertical: 14, alignItems: 'center' },
  logoutText: { color: '#d93025', fontSize: 16, fontWeight: '600' },
});
