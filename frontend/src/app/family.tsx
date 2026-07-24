import { useState } from 'react';
import { ActivityIndicator, FlatList, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';

import { ChipGroup, type ChipOption } from '@/components/ChipGroup';
import { BottomSheetModal, Button, Fab, ListRow, TextField } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useFamilyMembers } from '@/api/schedule';
import { useAddMember, useCreateMemberAccount } from '@/api/family';
import { useAuthStore } from '@/store/auth';
import type { FamilyMember } from '@/domain/schedule';
import type { FamilyRole } from '@/domain/types';

const ROLE_OPTIONS: ChipOption<FamilyRole>[] = [
  { value: 'CHILD', label: '자녀' },
  { value: 'PARENT', label: '부모' },
];

export default function FamilyScreen() {
  const theme = useTheme();
  const isParent = useAuthStore((s) => s.me?.role === 'PARENT');
  const membersQ = useFamilyMembers();
  const addMut = useAddMember();
  const accountMut = useCreateMemberAccount();

  const [memberOpen, setMemberOpen] = useState(false);
  const [name, setName] = useState('');
  const [role, setRole] = useState<FamilyRole>('CHILD');
  const [birthDate, setBirthDate] = useState('');

  const [accountFor, setAccountFor] = useState<FamilyMember | null>(null);
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  const submitMember = async () => {
    if (!name.trim()) {
      setError('이름을 입력하세요.');
      return;
    }
    setError(null);
    try {
      await addMut.mutateAsync({
        name: name.trim(),
        role,
        birthDate: birthDate.trim() || null,
        timezone: 'Asia/Seoul',
      });
      setMemberOpen(false);
      setName('');
      setBirthDate('');
      setRole('CHILD');
    } catch (e) {
      setError(e instanceof Error ? e.message : '추가에 실패했습니다.');
    }
  };

  const submitAccount = async () => {
    if (loginId.trim().length < 3) {
      setError('아이디는 3자 이상이어야 합니다.');
      return;
    }
    if (password.length < 8) {
      setError('비밀번호는 8자 이상이어야 합니다.');
      return;
    }
    if (!accountFor) return;
    setError(null);
    try {
      await accountMut.mutateAsync({ personId: accountFor.personId, loginId: loginId.trim(), password });
      setAccountFor(null);
      setLoginId('');
      setPassword('');
    } catch (e) {
      setError(e instanceof Error ? e.message : '계정 생성에 실패했습니다.');
    }
  };

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['bottom']}>
      {membersQ.isLoading ? (
        <View style={styles.center}>
          <ActivityIndicator />
        </View>
      ) : (
        <FlatList
          data={membersQ.data ?? []}
          keyExtractor={(m) => String(m.personId)}
          ItemSeparatorComponent={() => <View style={[styles.sep, { backgroundColor: theme.surfaceMuted }]} />}
          contentContainerStyle={styles.list}
          renderItem={({ item }) => (
            <ListRow
              title={item.name}
              subtitle={`${item.role === 'PARENT' ? '부모' : '자녀'}${item.hasAccount ? '' : ' · 계정없음'}`}
              trailing={
                isParent && !item.hasAccount ? (
                  <Button
                    label="계정 만들기"
                    variant="outline"
                    onPress={() => {
                      setAccountFor(item);
                      setError(null);
                    }}
                    style={styles.accountBtn}
                  />
                ) : undefined
              }
            />
          )}
        />
      )}

      {isParent && (
        <Fab
          icon="person-add"
          onPress={() => {
            setMemberOpen(true);
            setError(null);
          }}
        />
      )}

      <BottomSheetModal visible={memberOpen} onClose={() => setMemberOpen(false)} title="구성원 추가">
        <TextField label="이름" value={name} onChangeText={setName} placeholder="이름" />
        <Text style={[styles.label, { color: theme.textSecondary }]}>역할</Text>
        <ChipGroup options={ROLE_OPTIONS} value={role} onChange={setRole} />
        <TextField
          label="생년월일 (선택, YYYY-MM-DD)"
          value={birthDate}
          onChangeText={setBirthDate}
          placeholder="2018-03-01"
          autoCapitalize="none"
        />
        {error && <Text style={{ color: theme.danger, fontSize: 14, marginTop: 4 }}>{error}</Text>}
        <View style={styles.actions}>
          <Button label="취소" variant="outline" onPress={() => setMemberOpen(false)} style={styles.flexBtn} />
          <Button label="추가" onPress={submitMember} loading={addMut.isPending} disabled={addMut.isPending} style={styles.flexBtn} />
        </View>
      </BottomSheetModal>

      <BottomSheetModal
        visible={accountFor != null}
        onClose={() => setAccountFor(null)}
        title={`${accountFor?.name ?? ''} 계정 만들기`}>
        <TextField label="로그인 아이디" value={loginId} onChangeText={setLoginId} placeholder="loginId" autoCapitalize="none" autoCorrect={false} />
        <TextField label="비밀번호 (8자 이상)" value={password} onChangeText={setPassword} placeholder="password" secureTextEntry />
        {error && <Text style={{ color: theme.danger, fontSize: 14, marginTop: 4 }}>{error}</Text>}
        <View style={styles.actions}>
          <Button label="취소" variant="outline" onPress={() => setAccountFor(null)} style={styles.flexBtn} />
          <Button label="생성" onPress={submitAccount} loading={accountMut.isPending} disabled={accountMut.isPending} style={styles.flexBtn} />
        </View>
      </BottomSheetModal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  list: { padding: Spacing.three },
  sep: { height: 1 },
  accountBtn: { paddingVertical: 8, paddingHorizontal: 12 },
  label: { fontSize: 13, fontWeight: '600', marginTop: 6 },
  actions: { flexDirection: 'row', gap: 12, marginTop: 14 },
  flexBtn: { flex: 1 },
});
