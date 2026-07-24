import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { BottomSheetModal, Button, Chip, ComboBox, TextField } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useCollections, useCreateCollection } from '@/api/collection';
import { useCreateTag, useTags } from '@/api/tag';

const NONE = 0;

/**
 * 묶음(컬렉션) 검색형 단일 선택(ComboBox) — "없음"(value 0 → null) 포함.
 * 묶음 생성은 일정(Schedule)을 베이스로 하고 가계부·기록은 기존 묶음에 연결만 하는 방식이라,
 * "+ 새 묶음" 생성 버튼은 `allowCreate`(기본 true)를 켠 곳(일정 작성)에서만 노출한다.
 * `suggestedDates` — 일정 작성 화면에서 넘겨주는 그 일정의 날짜. 새 묶음 생성 시 startedAt/endedAt에 반영(묶음은 날짜 기준).
 */
export function CollectionPicker({
  value,
  onChange,
  allowCreate = true,
  suggestedDates,
}: {
  value: number | null;
  onChange: (value: number | null) => void;
  allowCreate?: boolean;
  suggestedDates?: { startedAt: string | null; endedAt: string | null };
}) {
  const theme = useTheme();
  const q = useCollections();
  const createMut = useCreateCollection();
  const tagsQ = useTags();
  const createTagMut = useCreateTag();

  const [creating, setCreating] = useState(false);
  const [name, setName] = useState('');
  const [selectedTagIds, setSelectedTagIds] = useState<Set<number>>(new Set());
  const [newTagName, setNewTagName] = useState('');
  const [error, setError] = useState<string | null>(null);

  const options = [
    { value: NONE, label: '없음' },
    ...(q.data ?? []).map((c) => ({ value: c.id, label: c.tags.length > 0 ? `${c.name} (${c.tags.join(', ')})` : c.name })),
  ];

  const openCreate = () => {
    setName('');
    setSelectedTagIds(new Set());
    setNewTagName('');
    setError(null);
    setCreating(true);
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
    if (!name.trim()) {
      setError('묶음 이름을 입력하세요.');
      return;
    }
    try {
      const created = await createMut.mutateAsync({
        name: name.trim(),
        startedAt: suggestedDates?.startedAt ?? null,
        endedAt: suggestedDates?.endedAt ?? null,
        tagIds: [...selectedTagIds],
      });
      setCreating(false);
      onChange(created.id);
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장에 실패했습니다.');
    }
  };

  return (
    <View style={styles.wrap}>
      <ComboBox
        options={options}
        value={value ?? NONE}
        onChange={(v) => onChange(v === NONE ? null : v)}
        placeholder="묶음 없음"
        searchPlaceholder="묶음 이름·태그 검색"
      />
      {allowCreate && <Button label="+ 새 묶음" variant="outline" onPress={openCreate} style={styles.newBtn} />}

      <BottomSheetModal visible={creating} onClose={() => setCreating(false)} title="새 묶음">
        <TextField label="이름" value={name} onChangeText={setName} placeholder="예) 제주 여행" />

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

        {error && <Text style={{ color: theme.danger, fontSize: 14 }}>{error}</Text>}

        <Button label="만들고 선택" onPress={save} loading={createMut.isPending} disabled={createMut.isPending} />
        <Button label="취소" variant="outline" onPress={() => setCreating(false)} />
      </BottomSheetModal>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: Spacing.two },
  newBtn: { alignSelf: 'flex-start', paddingHorizontal: Spacing.three, paddingVertical: 8 },
  label: { fontSize: 13, fontWeight: '600', marginTop: 4 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two },
  newTagRow: { flexDirection: 'row', gap: Spacing.two, alignItems: 'flex-end' },
  newTagInput: { flex: 1 },
  addTagBtn: { paddingHorizontal: Spacing.three },
});
