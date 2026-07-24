import { useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';

import { ChipGroup, type ChipOption } from '@/components/ChipGroup';
import { BottomSheetModal, Button, EmptyState, Fab, ListRow, TextField } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
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
  const theme = useTheme();
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
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['bottom']}>
      {accountsQ.isLoading ? (
        <View style={styles.center}>
          <ActivityIndicator />
        </View>
      ) : (
        <FlatList
          data={accountsQ.data ?? []}
          keyExtractor={(a) => String(a.id)}
          ItemSeparatorComponent={() => <View style={[styles.sep, { backgroundColor: theme.surfaceMuted }]} />}
          contentContainerStyle={(accountsQ.data?.length ?? 0) === 0 ? styles.emptyBox : styles.list}
          ListEmptyComponent={<EmptyState text="계좌가 없습니다. + 로 추가하세요." />}
          renderItem={({ item }) => (
            <ListRow
              title={item.name}
              subtitle={`${ASSET_TYPE_LABEL[item.assetType as AssetType] ?? item.assetType} · ${item.ownerType === 'FAMILY' ? '가족공용' : '개인'}`}
              onPress={() => openEdit(item)}
              trailing={
                <Pressable hitSlop={10} onPress={() => remove(item)}>
                  <Ionicons name="trash-outline" size={20} color={theme.danger} />
                </Pressable>
              }
            />
          )}
        />
      )}

      <Fab onPress={openCreate} />

      <BottomSheetModal visible={open} onClose={() => setOpen(false)} title={editingId == null ? '계좌 추가' : '계좌 수정'}>
        <TextField label="이름" value={name} onChangeText={setName} placeholder="예) 신한 주거래" />

        <Text style={[styles.label, { color: theme.textSecondary }]}>자산 유형</Text>
        <ChipGroup options={ASSET_OPTIONS} value={assetType} onChange={setAssetType} />

        <Text style={[styles.label, { color: theme.textSecondary }]}>소유</Text>
        <ChipGroup options={OWNER_OPTIONS} value={ownerType} onChange={setOwnerType} />

        <Text style={[styles.label, { color: theme.textSecondary }]}>공개 범위</Text>
        <ChipGroup options={VISIBILITY_OPTIONS} value={visibility} onChange={setVisibility} />

        {error && <Text style={{ color: theme.danger, fontSize: 14, marginTop: 4 }}>{error}</Text>}

        <View style={styles.modalActions}>
          <Button label="취소" variant="outline" onPress={() => setOpen(false)} style={styles.flexBtn} />
          <Button label="저장" onPress={save} loading={saving} disabled={saving} style={styles.flexBtn} />
        </View>
      </BottomSheetModal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  list: { paddingHorizontal: Spacing.three, paddingVertical: Spacing.two, paddingBottom: 96 },
  emptyBox: { flexGrow: 1 },
  sep: { height: 1 },
  label: { fontSize: 13, fontWeight: '600', marginTop: 6 },
  modalActions: { flexDirection: 'row', gap: 12, marginTop: 14 },
  flexBtn: { flex: 1 },
});
