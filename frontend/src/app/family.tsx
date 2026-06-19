import { useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';

import { ChipGroup, type ChipOption } from '@/components/ChipGroup';
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
    <SafeAreaView style={styles.safe} edges={['bottom']}>
      {membersQ.isLoading ? (
        <View style={styles.center}>
          <ActivityIndicator />
        </View>
      ) : (
        <FlatList
          data={membersQ.data ?? []}
          keyExtractor={(m) => String(m.personId)}
          ItemSeparatorComponent={() => <View style={styles.sep} />}
          contentContainerStyle={styles.list}
          renderItem={({ item }) => (
            <View style={styles.row}>
              <View style={styles.rowLeft}>
                <Text style={styles.name}>{item.name}</Text>
                <Text style={styles.role}>
                  {item.role === 'PARENT' ? '부모' : '자녀'}
                  {item.hasAccount ? '' : ' · 계정없음'}
                </Text>
              </View>
              {isParent && !item.hasAccount && (
                <Pressable style={styles.accountBtn} onPress={() => { setAccountFor(item); setError(null); }}>
                  <Text style={styles.accountBtnText}>계정 만들기</Text>
                </Pressable>
              )}
            </View>
          )}
        />
      )}

      {isParent && (
        <Pressable style={styles.fab} onPress={() => { setMemberOpen(true); setError(null); }}>
          <Ionicons name="person-add" size={24} color="#fff" />
        </Pressable>
      )}

      {/* 구성원 추가 */}
      <Modal visible={memberOpen} animationType="slide" transparent onRequestClose={() => setMemberOpen(false)}>
        <View style={styles.backdrop}>
          <View style={styles.card}>
            <Text style={styles.cardTitle}>구성원 추가</Text>
            <Text style={styles.label}>이름</Text>
            <TextInput style={styles.input} value={name} onChangeText={setName} placeholder="이름" placeholderTextColor="#9aa0a6" />
            <Text style={styles.label}>역할</Text>
            <ChipGroup options={ROLE_OPTIONS} value={role} onChange={setRole} />
            <Text style={styles.label}>생년월일 (선택, YYYY-MM-DD)</Text>
            <TextInput style={styles.input} value={birthDate} onChangeText={setBirthDate} placeholder="2018-03-01" placeholderTextColor="#9aa0a6" autoCapitalize="none" />
            {error && <Text style={styles.error}>{error}</Text>}
            <View style={styles.actions}>
              <Pressable style={[styles.btn, styles.cancel]} onPress={() => setMemberOpen(false)}>
                <Text style={styles.cancelText}>취소</Text>
              </Pressable>
              <Pressable style={[styles.btn, styles.save, addMut.isPending && styles.disabled]} disabled={addMut.isPending} onPress={submitMember}>
                {addMut.isPending ? <ActivityIndicator color="#fff" /> : <Text style={styles.saveText}>추가</Text>}
              </Pressable>
            </View>
          </View>
        </View>
      </Modal>

      {/* 계정 만들기 */}
      <Modal visible={accountFor != null} animationType="slide" transparent onRequestClose={() => setAccountFor(null)}>
        <View style={styles.backdrop}>
          <View style={styles.card}>
            <Text style={styles.cardTitle}>{accountFor?.name} 계정 만들기</Text>
            <Text style={styles.label}>로그인 아이디</Text>
            <TextInput style={styles.input} value={loginId} onChangeText={setLoginId} placeholder="loginId" placeholderTextColor="#9aa0a6" autoCapitalize="none" autoCorrect={false} />
            <Text style={styles.label}>비밀번호 (8자 이상)</Text>
            <TextInput style={styles.input} value={password} onChangeText={setPassword} placeholder="password" placeholderTextColor="#9aa0a6" secureTextEntry />
            {error && <Text style={styles.error}>{error}</Text>}
            <View style={styles.actions}>
              <Pressable style={[styles.btn, styles.cancel]} onPress={() => setAccountFor(null)}>
                <Text style={styles.cancelText}>취소</Text>
              </Pressable>
              <Pressable style={[styles.btn, styles.save, accountMut.isPending && styles.disabled]} disabled={accountMut.isPending} onPress={submitAccount}>
                {accountMut.isPending ? <ActivityIndicator color="#fff" /> : <Text style={styles.saveText}>생성</Text>}
              </Pressable>
            </View>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#fff' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  list: { padding: 16 },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingVertical: 14 },
  rowLeft: { gap: 3 },
  name: { fontSize: 16, color: '#202124', fontWeight: '500' },
  role: { fontSize: 13, color: '#5f6368' },
  accountBtn: { borderWidth: 1, borderColor: '#1a73e8', borderRadius: 8, paddingHorizontal: 12, paddingVertical: 8 },
  accountBtnText: { color: '#1a73e8', fontSize: 13, fontWeight: '600' },
  sep: { height: 1, backgroundColor: '#f1f3f4' },
  fab: { position: 'absolute', right: 20, bottom: 24, width: 56, height: 56, borderRadius: 28, backgroundColor: '#1a73e8', alignItems: 'center', justifyContent: 'center', elevation: 4 },
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.35)', justifyContent: 'flex-end' },
  card: { backgroundColor: '#fff', borderTopLeftRadius: 16, borderTopRightRadius: 16, padding: 20, gap: 8 },
  cardTitle: { fontSize: 18, fontWeight: '700', color: '#202124', marginBottom: 4 },
  label: { fontSize: 13, fontWeight: '600', color: '#3c4043', marginTop: 6 },
  input: { borderWidth: 1, borderColor: '#dadce0', borderRadius: 10, paddingHorizontal: 14, paddingVertical: 12, fontSize: 16, color: '#202124' },
  error: { color: '#d93025', fontSize: 14, marginTop: 4 },
  actions: { flexDirection: 'row', gap: 12, marginTop: 14 },
  btn: { flex: 1, borderRadius: 10, paddingVertical: 14, alignItems: 'center' },
  cancel: { borderWidth: 1, borderColor: '#dadce0' },
  cancelText: { color: '#3c4043', fontSize: 16, fontWeight: '600' },
  save: { backgroundColor: '#1a73e8' },
  saveText: { color: '#fff', fontSize: 16, fontWeight: '600' },
  disabled: { opacity: 0.6 },
});
