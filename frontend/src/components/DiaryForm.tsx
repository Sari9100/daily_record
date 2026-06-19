import { type ReactNode, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { format } from 'date-fns';

import { ChipGroup, type ChipOption } from '@/components/ChipGroup';
import { CollectionPicker } from '@/components/CollectionPicker';
import { useFamilyMembers } from '@/api/schedule';
import type { Diary, DiaryCreate } from '@/domain/diary';
import type { Visibility } from '@/domain/types';

const VISIBILITY_OPTIONS: ChipOption<Visibility>[] = [
  { value: 'PRIVATE', label: '개인' },
  { value: 'SHARED_PERSONAL', label: '공유개인' },
  { value: 'PARENTS', label: '부모공유' },
  { value: 'FAMILY', label: '가족' },
];
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

export function DiaryForm({
  initial,
  submitting,
  submitLabel,
  onSubmit,
  onDelete,
  deleting,
  children,
}: {
  initial?: Diary;
  submitting: boolean;
  submitLabel: string;
  onSubmit: (body: DiaryCreate) => Promise<void>;
  onDelete?: () => void;
  deleting?: boolean;
  children?: ReactNode;
}) {
  const membersQ = useFamilyMembers();
  const [title, setTitle] = useState(initial?.title ?? '');
  const [content, setContent] = useState(initial?.content ?? '');
  const [recordedOn, setRecordedOn] = useState(initial?.recordedOn ?? format(new Date(), 'yyyy-MM-dd'));
  const [visibility, setVisibility] = useState<Visibility>(initial?.visibility ?? 'PRIVATE');
  const [subjects, setSubjects] = useState<Set<number>>(new Set(initial?.subjects ?? []));
  const [collectionId, setCollectionId] = useState<number | null>(initial?.collectionId ?? null);
  const [error, setError] = useState<string | null>(null);

  const toggleSubject = (id: number) =>
    setSubjects((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const submit = async () => {
    if (!content.trim()) {
      setError('내용을 입력하세요.');
      return;
    }
    if (!DATE_RE.test(recordedOn)) {
      setError('날짜를 YYYY-MM-DD 형식으로 입력하세요.');
      return;
    }
    setError(null);
    try {
      await onSubmit({
        title: title.trim() || null,
        content: content.trim(),
        visibility,
        recordedOn,
        collectionId,
        subjectPersonIds: visibility === 'SHARED_PERSONAL' ? Array.from(subjects) : [],
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장에 실패했습니다.');
    }
  };

  return (
    <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <Field label="제목 (선택)">
          <TextInput style={styles.input} value={title} onChangeText={setTitle} placeholder="제목" placeholderTextColor="#9aa0a6" />
        </Field>

        <Field label="내용">
          <TextInput
            style={[styles.input, styles.multiline]}
            value={content}
            onChangeText={setContent}
            placeholder="오늘의 기록"
            placeholderTextColor="#9aa0a6"
            multiline
          />
        </Field>

        <Field label="날짜 (YYYY-MM-DD)">
          <TextInput style={styles.input} value={recordedOn} onChangeText={setRecordedOn} placeholder="2026-06-20" placeholderTextColor="#9aa0a6" autoCapitalize="none" />
        </Field>

        <Field label="공개 범위">
          <ChipGroup options={VISIBILITY_OPTIONS} value={visibility} onChange={setVisibility} />
        </Field>

        {visibility === 'SHARED_PERSONAL' && (
          <Field label="대상 (subject)">
            <View style={styles.chips}>
              {(membersQ.data ?? []).map((m) => {
                const on = subjects.has(m.personId);
                return (
                  <Pressable key={m.personId} onPress={() => toggleSubject(m.personId)} style={[styles.chip, on && styles.chipOn]}>
                    <Text style={[styles.chipText, on && styles.chipTextOn]}>{m.name}</Text>
                  </Pressable>
                );
              })}
            </View>
          </Field>
        )}

        <Field label="묶음 (선택)">
          <CollectionPicker value={collectionId} onChange={setCollectionId} />
        </Field>

        {error && <Text style={styles.error}>{error}</Text>}

        <Pressable style={[styles.btn, styles.save, submitting && styles.disabled]} disabled={submitting} onPress={submit}>
          {submitting ? <ActivityIndicator color="#fff" /> : <Text style={styles.saveText}>{submitLabel}</Text>}
        </Pressable>

        {/* 사진 섹션 등 추가 영역(편집 화면에서 주입) */}
        {children}

        {onDelete && (
          <Pressable style={[styles.btn, styles.delete, deleting && styles.disabled]} disabled={deleting} onPress={onDelete}>
            {deleting ? <ActivityIndicator color="#d93025" /> : <Text style={styles.deleteText}>삭제</Text>}
          </Pressable>
        )}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: '#fff' },
  container: { padding: 20, gap: 18 },
  field: { gap: 8 },
  label: { fontSize: 13, fontWeight: '600', color: '#3c4043' },
  input: { borderWidth: 1, borderColor: '#dadce0', borderRadius: 10, paddingHorizontal: 14, paddingVertical: 12, fontSize: 16, color: '#202124' },
  multiline: { minHeight: 120, textAlignVertical: 'top' },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: { paddingHorizontal: 14, paddingVertical: 9, borderRadius: 20, borderWidth: 1, borderColor: '#dadce0', backgroundColor: '#fff' },
  chipOn: { backgroundColor: '#1a73e8', borderColor: '#1a73e8' },
  chipText: { fontSize: 14, color: '#3c4043' },
  chipTextOn: { color: '#fff', fontWeight: '600' },
  error: { color: '#d93025', fontSize: 14 },
  btn: { borderRadius: 10, paddingVertical: 14, alignItems: 'center' },
  save: { backgroundColor: '#1a73e8' },
  saveText: { color: '#fff', fontSize: 16, fontWeight: '600' },
  delete: { borderWidth: 1, borderColor: '#d93025' },
  deleteText: { color: '#d93025', fontSize: 16, fontWeight: '600' },
  disabled: { opacity: 0.6 },
});
