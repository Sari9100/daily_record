import { StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';

import { Button, ListRow } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useAuthStore } from '@/store/auth';
import { signOut } from '@/api/session';

export default function MoreScreen() {
  const theme = useTheme();
  const me = useAuthStore((s) => s.me);
  const router = useRouter();

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['bottom']}>
      <View style={styles.section}>
        <Text style={[styles.name, { color: theme.text }]}>{me?.name ?? '사용자'}</Text>
        <Text style={[styles.sub, { color: theme.textSecondary }]}>
          {me?.familyName ? `${me.familyName} · ` : ''}
          {me?.role === 'PARENT' ? '부모' : me?.role === 'CHILD' ? '자녀' : ''}
        </Text>
      </View>

      <View>
        <ListRow title="이벤트 묶음" onPress={() => router.push('/collections')} />
        <ListRow title="가족·구성원" onPress={() => router.push('/family')} />
        <ListRow title="외부연동 (구글 캘린더)" onPress={() => router.push('/integrations')} />
        <ListRow title="개인 설정" onPress={() => router.push('/settings')} />
        <ListRow title="비밀번호 변경" onPress={() => router.push('/change-password')} />
      </View>

      <Button label="로그아웃" variant="danger" onPress={() => signOut()} style={styles.logout} />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, padding: Spacing.three, gap: Spacing.four },
  section: { gap: 4, paddingVertical: Spacing.two },
  name: { fontSize: 22, fontWeight: '700' },
  sub: { fontSize: 14 },
  logout: { marginTop: 'auto' },
});
