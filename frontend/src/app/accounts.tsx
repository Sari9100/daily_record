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
import {
  useAccounts,
  useCreateAccount,
  useDeleteAccount,
  useUpdateAccount,
} from '@/api/ledger';
import { ASSET_TYPE_LABEL, type Account, type AccountOwnerType, type AssetType } from '@/domain/ledger';
import type { Visibility } from '@/domain/types';
import { useAuthStore } from '@/store/auth';
import { confirmAsync } from '@/lib/confirm';

const ASSET_OPTIONS: ChipOption<AssetType>[] = [
  { value: 'BANK', label: '은행' },
  { value: 'SECURITIES', label: '증권' },
  { value: 'CASH', label: '현금' },
];
const OWNER_OPTIONS: ChipOption<AccountOwnerType>[] = [
  { value: 'PERSON', label: '개인' },
  { value: 'FAMILY', label: '가족공용' },
];
const VISIBILITY_OPTIONS: ChipOption<Visibility>[] = [
  { value: 'PRIVATE', label: '개인' },
  { value: 'PARENTS', label: '부모공유' },
  { value: 'FAMILY', label: '가족' },
];

export default function AccountsScreen() {
  const me = useAuthStore((s) => s.me);
  const accountsQ = useAccounts();
  const createMut = useCreateAccount();
  const updateMut = useUpdateAccount();
  const deleteMut = useDeleteAccount();

  const [open, setOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [name, setName] = useState('');
  const [assetType, setAssetType] = useState<AssetType>('BANK');
  const [ownerType, setOwnerType] = useState<AccountOwnerType>('PERSON');
  const [visibility, setVisibility] = useState<Visibility>('PRIVATE');
  const [error, setError] = useState<string | null>(null);

  const openCreate = () => {
    setEditingId(null);
    setName('');
    setAssetType('BANK');
    setOwnerType('PERSON');
    setVisibility('PRIVATE');
    setError(null);
    setOpen(true);
  };

  const openEdit = (a: Account) => {
    setEditingId(a.id);
    setName(a.name);
    setAssetType(a.assetType as AssetType);
    setOwnerType(a.ownerType as AccountOwnerType);
    setVisibility(a.visibility);
    setError(null);
    setOpen(true);
  };

  const save = async () => {
    if (!name.trim()) {
      setError('계좌 이름을 입력하세요.');
      return;
    }
    const body = {
      name: name.trim(),
      assetType,
      ownerType,
      ownerPersonId: ownerType === 'PERSON' ? (me?.personId ?? null) : null,
      visibility,
    };
    try {
      if (editingId == null) await createMut.mutateAsync(body);
      else await updateMut.mutateAsync({ id: editingId, body });
      setOpen(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장에 실패했습니다.');
    }
  };

  const remove = async (a: Account) => {
    if (await confirmAsync(`'${a.name}' 계좌를 삭제할까요? (과거 거래 기록은 유지됩니다)`)) {
      await deleteMut.mutateAsync(a.id);
    }
  };

  const saving = createMut.isPending || updateMut.isPending;

  return (
    <SafeAreaView style={styles.safe} edges={['bottom']}>
      {accountsQ.isLoading ? (
        <View style={styles.center}>
          <ActivityIndicator />
        </View>
      ) : (
        <FlatList
          data={accountsQ.data ?? []}
          keyExtractor={(a) => String(a.id)}
          ItemSeparatorComponent={() => <View style={styles.sep} />}
          contentContainerStyle={(accountsQ.data?.length ?? 0) === 0 ? styles.emptyBox : styles.list}
          ListEmptyComponent={<Text style={styles.emptyText}>계좌가 없습니다. + 로 추가하세요.</Text>}
          renderItem={({ item }) => (
            <Pressable style={styles.row} onPress={() => openEdit(item)}>
              <View style={styles.rowLeft}>
                <Text style={styles.rowTitle}>{item.name}</Text>
                <Text style={styles.rowSub}>
                  {ASSET_TYPE_LABEL[item.assetType as AssetType] ?? item.assetType} ·{' '}
                  {item.ownerType === 'FAMILY' ? '가족공용' : '개인'}
                </Text>
              </View>
              <Pressable hitSlop={10} onPress={() => remove(item)}>
                <Ionicons name="trash-outline" size={20} color="#d93025" />
              </Pressable>
            </Pressable>
          )}
        />
      )}

      <Pressable style={styles.fab} onPress={openCreate}>
        <Ionicons name="add" size={28} color="#fff" />
      </Pressable>

      <Modal visible={open} animationType="slide" transparent onRequestClose={() => setOpen(false)}>
        <View style={styles.modalBackdrop}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>{editingId == null ? '계좌 추가' : '계좌 수정'}</Text>

            <Text style={styles.label}>이름</Text>
            <TextInput
              style={styles.input}
              value={name}
              onChangeText={setName}
              placeholder="예) 신한 주거래"
              placeholderTextColor="#9aa0a6"
            />

            <Text style={styles.label}>자산 유형</Text>
            <ChipGroup options={ASSET_OPTIONS} value={assetType} onChange={setAssetType} />

            <Text style={styles.label}>소유</Text>
            <ChipGroup options={OWNER_OPTIONS} value={ownerType} onChange={setOwnerType} />

            <Text style={styles.label}>공개 범위</Text>
            <ChipGroup options={VISIBILITY_OPTIONS} value={visibility} onChange={setVisibility} />

            {error && <Text style={styles.error}>{error}</Text>}

            <View style={styles.modalActions}>
              <Pressable style={[styles.btn, styles.cancel]} onPress={() => setOpen(false)}>
                <Text style={styles.cancelText}>취소</Text>
              </Pressable>
              <Pressable style={[styles.btn, styles.save, saving && styles.disabled]} disabled={saving} onPress={save}>
                {saving ? <ActivityIndicator color="#fff" /> : <Text style={styles.saveText}>저장</Text>}
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
  list: { paddingHorizontal: 16, paddingVertical: 8, paddingBottom: 96 },
  emptyBox: { flexGrow: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  emptyText: { color: '#5f6368', fontSize: 15 },
  row: { flexDirection: 'row', alignItems: 'center', paddingVertical: 14, gap: 12 },
  rowLeft: { flex: 1, gap: 3 },
  rowTitle: { fontSize: 16, color: '#202124', fontWeight: '500' },
  rowSub: { fontSize: 13, color: '#5f6368' },
  sep: { height: 1, backgroundColor: '#f1f3f4' },
  fab: {
    position: 'absolute',
    right: 20,
    bottom: 24,
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: '#1a73e8',
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 4,
  },
  modalBackdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.35)', justifyContent: 'flex-end' },
  modalCard: { backgroundColor: '#fff', borderTopLeftRadius: 16, borderTopRightRadius: 16, padding: 20, gap: 10 },
  modalTitle: { fontSize: 18, fontWeight: '700', color: '#202124', marginBottom: 4 },
  label: { fontSize: 13, fontWeight: '600', color: '#3c4043', marginTop: 6 },
  input: {
    borderWidth: 1,
    borderColor: '#dadce0',
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 16,
    color: '#202124',
  },
  error: { color: '#d93025', fontSize: 14, marginTop: 4 },
  modalActions: { flexDirection: 'row', gap: 12, marginTop: 14 },
  btn: { flex: 1, borderRadius: 10, paddingVertical: 14, alignItems: 'center' },
  cancel: { borderWidth: 1, borderColor: '#dadce0' },
  cancelText: { color: '#3c4043', fontSize: 16, fontWeight: '600' },
  save: { backgroundColor: '#1a73e8' },
  saveText: { color: '#fff', fontSize: 16, fontWeight: '600' },
  disabled: { opacity: 0.6 },
});
