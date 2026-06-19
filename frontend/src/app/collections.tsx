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
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';

import {
  useCollections,
  useCreateCollection,
  useDeleteCollection,
  useUpdateCollection,
} from '@/api/collection';
import type { Collection } from '@/domain/collection';
import { confirmAsync } from '@/lib/confirm';

export default function CollectionsScreen() {
  const router = useRouter();
  const listQ = useCollections();
  const createMut = useCreateCollection();
  const updateMut = useUpdateCollection();
  const deleteMut = useDeleteCollection();

  const [open, setOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);

  const openCreate = () => {
    setEditingId(null);
    setName('');
    setDescription('');
    setError(null);
    setOpen(true);
  };
  const openEdit = (c: Collection) => {
    setEditingId(c.id);
    setName(c.name);
    setDescription(c.description ?? '');
    setError(null);
    setOpen(true);
  };

  const save = async () => {
    if (!name.trim()) {
      setError('묶음 이름을 입력하세요.');
      return;
    }
    const body = { name: name.trim(), description: description.trim() || null };
    try {
      if (editingId == null) await createMut.mutateAsync(body);
      else await updateMut.mutateAsync({ id: editingId, body });
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

  const saving = createMut.isPending || updateMut.isPending;

  return (
    <SafeAreaView style={styles.safe} edges={['bottom']}>
      {listQ.isLoading ? (
        <View style={styles.center}>
          <ActivityIndicator />
        </View>
      ) : (
        <FlatList
          data={listQ.data ?? []}
          keyExtractor={(c) => String(c.id)}
          ItemSeparatorComponent={() => <View style={styles.sep} />}
          contentContainerStyle={(listQ.data?.length ?? 0) === 0 ? styles.emptyBox : styles.list}
          ListHeaderComponent={<Text style={styles.note}>여행·행사 단위로 거래·일정·기록을 묶습니다. 항목은 각 작성 화면에서 연결하세요.</Text>}
          ListEmptyComponent={<Text style={styles.emptyText}>묶음이 없습니다. + 로 추가하세요.</Text>}
          renderItem={({ item }) => (
            <Pressable
              style={styles.row}
              onPress={() => router.push({ pathname: '/collection/[id]', params: { id: item.id } })}>
              <View style={styles.rowLeft}>
                <Text style={styles.rowTitle}>{item.name}</Text>
                {item.description ? <Text style={styles.rowSub}>{item.description}</Text> : null}
              </View>
              <Pressable hitSlop={8} onPress={() => openEdit(item)} style={styles.iconBtn}>
                <Ionicons name="pencil" size={18} color="#5f6368" />
              </Pressable>
              <Pressable hitSlop={8} onPress={() => remove(item)} style={styles.iconBtn}>
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
            <Text style={styles.modalTitle}>{editingId == null ? '묶음 추가' : '묶음 수정'}</Text>

            <Text style={styles.label}>이름</Text>
            <TextInput style={styles.input} value={name} onChangeText={setName} placeholder="예) 제주 여행" placeholderTextColor="#9aa0a6" />

            <Text style={styles.label}>설명 (선택)</Text>
            <TextInput style={styles.input} value={description} onChangeText={setDescription} placeholder="설명" placeholderTextColor="#9aa0a6" />

            {error && <Text style={styles.error}>{error}</Text>}

            <View style={styles.modalActions}>
              <Pressable style={[styles.btn, styles.cancel]} onPress={() => setOpen(false)}>
                <Text style={styles.cancelText}>취소</Text>
              </Pressable>
              <Pressable style={[styles.btn, styles.saveBtn, saving && styles.disabled]} disabled={saving} onPress={save}>
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
  note: { fontSize: 13, color: '#9aa0a6', paddingVertical: 8 },
  emptyBox: { flexGrow: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  emptyText: { color: '#5f6368', fontSize: 15 },
  row: { flexDirection: 'row', alignItems: 'center', paddingVertical: 14, gap: 14 },
  rowLeft: { flex: 1, gap: 3 },
  iconBtn: { padding: 2 },
  rowTitle: { fontSize: 16, color: '#202124', fontWeight: '500' },
  rowSub: { fontSize: 13, color: '#5f6368' },
  sep: { height: 1, backgroundColor: '#f1f3f4' },
  fab: { position: 'absolute', right: 20, bottom: 24, width: 56, height: 56, borderRadius: 28, backgroundColor: '#1a73e8', alignItems: 'center', justifyContent: 'center', elevation: 4 },
  modalBackdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.35)', justifyContent: 'flex-end' },
  modalCard: { backgroundColor: '#fff', borderTopLeftRadius: 16, borderTopRightRadius: 16, padding: 20, gap: 10 },
  modalTitle: { fontSize: 18, fontWeight: '700', color: '#202124', marginBottom: 4 },
  label: { fontSize: 13, fontWeight: '600', color: '#3c4043', marginTop: 6 },
  input: { borderWidth: 1, borderColor: '#dadce0', borderRadius: 10, paddingHorizontal: 14, paddingVertical: 12, fontSize: 16, color: '#202124' },
  error: { color: '#d93025', fontSize: 14, marginTop: 4 },
  modalActions: { flexDirection: 'row', gap: 12, marginTop: 14 },
  btn: { flex: 1, borderRadius: 10, paddingVertical: 14, alignItems: 'center' },
  cancel: { borderWidth: 1, borderColor: '#dadce0' },
  cancelText: { color: '#3c4043', fontSize: 16, fontWeight: '600' },
  saveBtn: { backgroundColor: '#1a73e8' },
  saveText: { color: '#fff', fontSize: 16, fontWeight: '600' },
  disabled: { opacity: 0.6 },
});
