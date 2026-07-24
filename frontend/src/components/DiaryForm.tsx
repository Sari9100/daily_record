import { type ReactNode, useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from 'react-native';
import { format } from 'date-fns';

import { ChipGroup, type ChipOption } from '@/components/ChipGroup';
import { CollectionPicker } from '@/components/CollectionPicker';
import { Button, Chip, TextField } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
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
  /** 편집 시 전체 Diary, 신규 작성 시 collectionId 등 일부 필드만 prefill 가능. */
  initial?: Partial<Diary>;
  submitting: boolean;
  submitLabel: string;
  onSubmit: (body: DiaryCreate) => Promise<void>;
  onDelete?: () => void;
  deleting?: boolean;
  children?: ReactNode;
}) {
  const theme = useTheme();
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
    <KeyboardAvoidingView style={[styles.flex, { backgroundColor: theme.background }]} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <TextField label="제목 (선택)" value={title} onChangeText={setTitle} placeholder="제목" />

        <TextField
          label="내용"
          value={content}
          onChangeText={setContent}
          placeholder="오늘의 기록"
          multiline
          style={styles.multiline}
        />

        <TextField label="날짜 (YYYY-MM-DD)" value={recordedOn} onChangeText={setRecordedOn} placeholder="2026-06-20" autoCapitalize="none" />

        <Field label="공개 범위">
          <ChipGroup options={VISIBILITY_OPTIONS} value={visibility} onChange={setVisibility} />
        </Field>

        {visibility === 'SHARED_PERSONAL' && (
          <Field label="대상 (subject)">
            <View style={styles.chips}>
              {(membersQ.data ?? []).map((m) => (
                <Chip key={m.personId} label={m.name} selected={subjects.has(m.personId)} onPress={() => toggleSubject(m.personId)} />
              ))}
            </View>
          </Field>
        )}

        <Field label="묶음 (선택, 일정에서 생성)">
          <CollectionPicker value={collectionId} onChange={setCollectionId} allowCreate={false} />
        </Field>

        {error && <Text style={{ color: theme.danger, fontSize: 14 }}>{error}</Text>}

        <Button label={submitLabel} onPress={submit} loading={submitting} disabled={submitting} />

        {/* 사진 섹션 등 추가 영역(편집 화면에서 주입) */}
        {children}

        {onDelete && <Button label="삭제" variant="danger" onPress={onDelete} loading={deleting} disabled={deleting} />}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  const theme = useTheme();
  return (
    <View style={styles.field}>
      <Text style={[styles.label, { color: theme.textSecondary }]}>{label}</Text>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  container: { padding: Spacing.three, gap: Spacing.four - 6 },
  field: { gap: Spacing.two },
  label: { fontSize: 13, fontWeight: '600' },
  multiline: { minHeight: 120, textAlignVertical: 'top' },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two },
});
