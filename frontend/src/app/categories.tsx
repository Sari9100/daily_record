import { useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';

import { ChipGroup, type ChipOption } from '@/components/ChipGroup';
import { BottomSheetModal, Button, EmptyState, Fab, ListRow, TextField } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import {
  useCategories,
  useCreateCategory,
  useDeleteCategory,
  useUpdateCategory,
} from '@/api/ledger';
import { CATEGORY_TYPE_LABEL, type Category, type CategoryType } from '@/domain/ledger';
import { confirmAsync } from '@/lib/confirm';

const TYPE_OPTIONS: ChipOption<CategoryType>[] = [
  { value: 'EXPENSE', label: '지출' },
  { value: 'INCOME', label: '수입' },
];

export default function CategoriesScreen() {
  const theme = useTheme();
  const categoriesQ = useCategories();
  const createMut = useCreateCategory();
  const updateMut = useUpdateCategory();
  const deleteMut = useDeleteCategory();

  const [open, setOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [name, setName] = useState('');
  const [type, setType] = useState<CategoryType>('EXPENSE');
  const [error, setError] = useState<string | null>(null);

  const openCreate = () => {
    setEditingId(null);
    setName('');
    setType('EXPENSE');
    setError(null);
    setOpen(true);
  };

  const openEdit = (c: Category) => {
    setEditingId(c.id);
    setName(c.name);
    setType(c.type);
    setError(null);
    setOpen(true);
  };

  const save = async () => {
    if (!name.trim()) {
      setError('카테고리 이름을 입력하세요.');
      return;
    }
    const body = { name: name.trim(), type };
    try {
      if (editingId == null) await createMut.mutateAsync(body);
      else await updateMut.mutateAsync({ id: editingId, body });
      setOpen(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장에 실패했습니다.');
    }
  };

  const remove = async (c: Category) => {
    if (await confirmAsync(`'${c.name}' 카테고리를 삭제할까요?`)) {
      await deleteMut.mutateAsync(c.id);
    }
  };

  const saving = createMut.isPending || updateMut.isPending;

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['bottom']}>
      {categoriesQ.isLoading ? (
        <View style={styles.center}>
          <ActivityIndicator />
        </View>
      ) : (
        <FlatList
          data={categoriesQ.data ?? []}
          keyExtractor={(c) => String(c.id)}
          ItemSeparatorComponent={() => <View style={[styles.sep, { backgroundColor: theme.surfaceMuted }]} />}
          contentContainerStyle={(categoriesQ.data?.length ?? 0) === 0 ? styles.emptyBox : styles.list}
          ListEmptyComponent={<EmptyState text="카테고리가 없습니다. + 로 추가하세요." />}
          renderItem={({ item }) => (
            <ListRow
              title={item.name}
              subtitle={`${CATEGORY_TYPE_LABEL[item.type]}${item.isSystem ? ' · 기본' : ''}`}
              disabled={item.isSystem}
              onPress={item.isSystem ? undefined : () => openEdit(item)}
              trailing={
                item.isSystem ? (
                  <Text style={[styles.systemTag, { color: theme.textMuted }]}>기본</Text>
                ) : (
                  <Pressable hitSlop={10} onPress={() => remove(item)}>
                    <Ionicons name="trash-outline" size={20} color={theme.danger} />
                  </Pressable>
                )
              }
            />
          )}
        />
      )}

      <Fab onPress={openCreate} />

      <BottomSheetModal visible={open} onClose={() => setOpen(false)} title={editingId == null ? '카테고리 추가' : '카테고리 수정'}>
        <TextField label="이름" value={name} onChangeText={setName} placeholder="예) 식비" />

        <Text style={[styles.label, { color: theme.textSecondary }]}>유형</Text>
        <ChipGroup options={TYPE_OPTIONS} value={type} onChange={setType} />

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
  systemTag: { fontSize: 12 },
  label: { fontSize: 13, fontWeight: '600', marginTop: 6 },
  modalActions: { flexDirection: 'row', gap: 12, marginTop: 14 },
  flexBtn: { flex: 1 },
});
