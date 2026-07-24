import { useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';

import { BottomSheetModal, Button, Chip, EmptyState, ListRow, TextField } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useCollections, useDeleteCollection, useUpdateCollection } from '@/api/collection';
import { useCreateTag, useTags } from '@/api/tag';
import type { Collection } from '@/domain/collection';
import { confirmAsync } from '@/lib/confirm';

/** 묶음 생성은 일정(Schedule) 작성 화면을 베이스로 한다 — 여기선 기존 묶음 조회·수정·삭제만. */
export default function CollectionsScreen() {
  const theme = useTheme();
  const router = useRouter();
  const listQ = useCollections();
  const updateMut = useUpdateCollection();
  const deleteMut = useDeleteCollection();
  const tagsQ = useTags();
  const createTagMut = useCreateTag();

  const [open, setOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [selectedTagIds, setSelectedTagIds] = useState<Set<number>>(new Set());
  const [newTagName, setNewTagName] = useState('');
  const [error, setError] = useState<string | null>(null);

  const openEdit = (c: Collection) => {
    setEditingId(c.id);
    setName(c.name);
    setDescription(c.description ?? '');
    // 태그는 이름으로만 내려오므로, 현재 태그 목록에서 이름이 일치하는 id를 찾아 프리필.
    const ids = (tagsQ.data ?? []).filter((t) => c.tags.includes(t.name)).map((t) => t.id);
    setSelectedTagIds(new Set(ids));
    setNewTagName('');
    setError(null);
    setOpen(true);
  };

  const toggleTag = (id: number) => {
    setSelectedTagIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const addNewTag = async () => {
    const trimmed = newTagName.trim();
    if (!trimmed) return;
    try {
      const tag = await createTagMut.mutateAsync(trimmed);
      setSelectedTagIds((prev) => new Set(prev).add(tag.id));
      setNewTagName('');
    } catch (e) {
      setError(e instanceof Error ? e.message : '태그 생성에 실패했습니다.');
    }
  };

  const save = async () => {
    if (!name.trim() || editingId == null) {
      setError('묶음 이름을 입력하세요.');
      return;
    }
    const body = { name: name.trim(), description: description.trim() || null, tagIds: [...selectedTagIds] };
    try {
      await updateMut.mutateAsync({ id: editingId, body });
      setOpen(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장에 실패했습니다.');
    }
  };

  const remove = async (c: Collection) => {
    if (await confirmAsync(`'${c.name}' 묶음을 삭제할까요? (연결된 항목은 유지됩니다)`)) {
      await deleteMut.mutateAsync(c.id);
    }
  };

  const saving = updateMut.isPending;

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['bottom']}>
      {listQ.isLoading ? (
        <View style={styles.center}>
          <ActivityIndicator />
        </View>
      ) : (
        <FlatList
          data={listQ.data ?? []}
          keyExtractor={(c) => String(c.id)}
          ItemSeparatorComponent={() => <View style={[styles.sep, { backgroundColor: theme.surfaceMuted }]} />}
          contentContainerStyle={(listQ.data?.length ?? 0) === 0 ? styles.emptyBox : styles.list}
          ListHeaderComponent={
            <Text style={[styles.note, { color: theme.textMuted }]}>
              여행·행사 단위로 거래·일정·기록을 묶습니다. 새 묶음은 일정 작성 화면에서 만들고, 가계부·기록은 만들어진 묶음에 연결만 합니다.
            </Text>
          }
          ListEmptyComponent={<EmptyState text="묶음이 없습니다. 일정 작성 화면에서 새 묶음을 만들어보세요." />}
          renderItem={({ item }) => (
            <ListRow
              title={item.name}
              subtitle={[item.description, item.tags.join(', ')].filter(Boolean).join(' · ') || undefined}
              onPress={() => router.push({ pathname: '/collection/[id]', params: { id: item.id } })}
              trailing={
                <View style={styles.actions}>
                  <Pressable hitSlop={8} onPress={() => openEdit(item)} style={styles.iconBtn}>
                    <Ionicons name="pencil" size={18} color={theme.textSecondary} />
                  </Pressable>
                  <Pressable hitSlop={8} onPress={() => remove(item)} style={styles.iconBtn}>
                    <Ionicons name="trash-outline" size={20} color={theme.danger} />
                  </Pressable>
                </View>
              }
            />
          )}
        />
      )}

      <BottomSheetModal visible={open} onClose={() => setOpen(false)} title="묶음 수정">
        <TextField label="이름" value={name} onChangeText={setName} placeholder="예) 제주 여행" />
        <TextField label="설명 (선택)" value={description} onChangeText={setDescription} placeholder="설명" />

        <Text style={[styles.label, { color: theme.textSecondary }]}>태그 (선택, 검색 목적)</Text>
        <View style={styles.chips}>
          {(tagsQ.data ?? []).map((t) => (
            <Chip key={t.id} label={t.name} selected={selectedTagIds.has(t.id)} onPress={() => toggleTag(t.id)} />
          ))}
        </View>
        <View style={styles.newTagRow}>
          <View style={styles.newTagInput}>
            <TextField label="새 태그" value={newTagName} onChangeText={setNewTagName} placeholder="예) 여행" onSubmitEditing={addNewTag} />
          </View>
          <Button label="추가" variant="outline" onPress={addNewTag} loading={createTagMut.isPending} disabled={createTagMut.isPending} style={styles.addTagBtn} />
        </View>

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
  note: { fontSize: 13, paddingVertical: Spacing.two },
  emptyBox: { flexGrow: 1 },
  sep: { height: 1 },
  actions: { flexDirection: 'row', gap: Spacing.two },
  iconBtn: { padding: 2 },
  label: { fontSize: 13, fontWeight: '600', marginTop: 4 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two },
  newTagRow: { flexDirection: 'row', gap: Spacing.two, alignItems: 'flex-end' },
  newTagInput: { flex: 1 },
  addTagBtn: { paddingHorizontal: Spacing.three },
  modalActions: { flexDirection: 'row', gap: 12, marginTop: 14 },
  flexBtn: { flex: 1 },
});
