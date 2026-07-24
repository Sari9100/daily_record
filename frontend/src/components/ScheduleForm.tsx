import { type ReactNode, useMemo, useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Switch, Text, View } from 'react-native';
import { format, parseISO } from 'date-fns';

import { ChipGroup, type ChipOption } from '@/components/ChipGroup';
import { CollectionPicker } from '@/components/CollectionPicker';
import { Button, Chip, TextField } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useFamilyMembers } from '@/api/schedule';
import type { Schedule, ScheduleCreate, ScheduleType } from '@/domain/schedule';
import type { Visibility } from '@/domain/types';

const TYPE_OPTIONS: ChipOption<ScheduleType>[] = [
  { value: 'EVENT', label: '일정' },
  { value: 'TODO', label: '할일' },
  { value: 'REMINDER', label: '리마인더' },
];
const VISIBILITY_OPTIONS: ChipOption<Visibility>[] = [
  { value: 'PRIVATE', label: '개인' },
  { value: 'SHARED_PERSONAL', label: '공유개인' },
  { value: 'PARENTS', label: '부모공유' },
  { value: 'FAMILY', label: '가족' },
];

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const TIME_RE = /^\d{2}:\d{2}$/;

function toIso(date: string, time: string): string | null {
  const d = new Date(`${date}T${time}:00`);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}

export function ScheduleForm({
  initial,
  submitting,
  submitLabel,
  onSubmit,
  onDelete,
  deleting,
}: {
  /** 편집 시 전체 Schedule, 신규 작성 시 collectionId 등 일부 필드만 prefill 가능. */
  initial?: Partial<Schedule>;
  submitting: boolean;
  submitLabel: string;
  onSubmit: (body: ScheduleCreate) => Promise<void>;
  onDelete?: () => void;
  deleting?: boolean;
}) {
  const theme = useTheme();
  const membersQ = useFamilyMembers();
  const today = format(new Date(), 'yyyy-MM-dd');
  const timed = initial && !initial.allDay && initial.startedAt ? parseISO(initial.startedAt) : null;

  const [title, setTitle] = useState(initial?.title ?? '');
  const [type, setType] = useState<ScheduleType>(initial?.scheduleType ?? 'EVENT');
  const [allDay, setAllDay] = useState(initial?.allDay ?? false);
  const [date, setDate] = useState(timed ? format(timed, 'yyyy-MM-dd') : today);
  const [startTime, setStartTime] = useState(timed ? format(timed, 'HH:mm') : '09:00');
  const [endTime, setEndTime] = useState(
    initial?.endedAt ? format(parseISO(initial.endedAt), 'HH:mm') : '10:00',
  );
  const [startDate, setStartDate] = useState(initial?.allDay && initial.startDate ? initial.startDate : today);
  const [endDate, setEndDate] = useState(initial?.allDay && initial.endDate ? initial.endDate : '');
  const [visibility, setVisibility] = useState<Visibility>(initial?.visibility ?? 'PRIVATE');
  const [subjects, setSubjects] = useState<Set<number>>(new Set(initial?.subjects ?? []));
  const [location, setLocation] = useState(initial?.location ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [collectionId, setCollectionId] = useState<number | null>(initial?.collectionId ?? null);
  const [error, setError] = useState<string | null>(null);

  // 새 묶음 생성 시 이 일정의 날짜를 그대로 넘겨준다(묶음은 날짜 기준).
  const suggestedDates = useMemo(() => {
    if (allDay) {
      const start = DATE_RE.test(startDate) ? toIso(startDate, '00:00') : null;
      const endBase = endDate && DATE_RE.test(endDate) ? endDate : startDate;
      const end = DATE_RE.test(endBase) ? toIso(endBase, '23:59') : null;
      return { startedAt: start, endedAt: end };
    }
    const start = DATE_RE.test(date) && TIME_RE.test(startTime) ? toIso(date, startTime) : null;
    const end = DATE_RE.test(date) && TIME_RE.test(endTime) ? toIso(date, endTime) : null;
    return { startedAt: start, endedAt: end };
  }, [allDay, startDate, endDate, date, startTime, endTime]);

  const toggleSubject = (id: number) => {
    setSubjects((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const build = (): ScheduleCreate | string => {
    if (!title.trim()) return '제목을 입력하세요.';
    const base: ScheduleCreate = {
      title: title.trim(),
      description: description.trim() || null,
      location: location.trim() || null,
      allDay,
      visibility,
      scheduleType: type,
      collectionId,
      subjectPersonIds: visibility === 'SHARED_PERSONAL' ? Array.from(subjects) : [],
      participantPersonIds: [],
    };
    if (allDay) {
      if (!DATE_RE.test(startDate)) return '시작 날짜를 YYYY-MM-DD 형식으로 입력하세요.';
      if (endDate && !DATE_RE.test(endDate)) return '종료 날짜 형식이 올바르지 않습니다.';
      if (endDate && endDate < startDate) return '종료 날짜는 시작보다 빠를 수 없습니다.';
      return { ...base, startDate, endDate: endDate || null, startedAt: null, endedAt: null };
    }
    if (!DATE_RE.test(date)) return '날짜를 YYYY-MM-DD 형식으로 입력하세요.';
    if (!TIME_RE.test(startTime)) return '시작 시각을 HH:mm 형식으로 입력하세요.';
    if (endTime && !TIME_RE.test(endTime)) return '종료 시각 형식이 올바르지 않습니다.';
    const startedAt = toIso(date, startTime);
    if (!startedAt) return '시작 일시가 올바르지 않습니다.';
    const endedAt = endTime ? toIso(date, endTime) : null;
    if (endTime && endedAt && endedAt < startedAt) return '종료 시각은 시작보다 빠를 수 없습니다.';
    return { ...base, startedAt, endedAt, startDate: null, endDate: null };
  };

  const submit = async () => {
    const result = build();
    if (typeof result === 'string') {
      setError(result);
      return;
    }
    setError(null);
    try {
      await onSubmit(result);
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장에 실패했습니다.');
    }
  };

  return (
    <KeyboardAvoidingView style={[styles.flex, { backgroundColor: theme.background }]} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <TextField label="제목" value={title} onChangeText={setTitle} placeholder="제목" />

        <Field label="유형">
          <ChipGroup options={TYPE_OPTIONS} value={type} onChange={setType} />
        </Field>

        <View style={styles.switchRow}>
          <Text style={[styles.label, { color: theme.textSecondary }]}>종일</Text>
          <Switch value={allDay} onValueChange={setAllDay} />
        </View>

        {allDay ? (
          <>
            <TextField label="시작 날짜 (YYYY-MM-DD)" value={startDate} onChangeText={setStartDate} placeholder="2026-06-20" autoCapitalize="none" />
            <TextField label="종료 날짜 (선택)" value={endDate} onChangeText={setEndDate} placeholder="비우면 하루 일정" autoCapitalize="none" />
          </>
        ) : (
          <>
            <TextField label="날짜 (YYYY-MM-DD)" value={date} onChangeText={setDate} placeholder="2026-06-20" autoCapitalize="none" />
            <View style={styles.timeRow}>
              <View style={styles.flex}>
                <TextField label="시작 (HH:mm)" value={startTime} onChangeText={setStartTime} placeholder="09:00" />
              </View>
              <View style={styles.flex}>
                <TextField label="종료 (선택)" value={endTime} onChangeText={setEndTime} placeholder="10:00" />
              </View>
            </View>
          </>
        )}

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

        <TextField label="장소 (선택)" value={location} onChangeText={setLocation} placeholder="장소" />
        <TextField label="메모 (선택)" value={description} onChangeText={setDescription} placeholder="메모" />

        <Field label="묶음 (선택)">
          <CollectionPicker value={collectionId} onChange={setCollectionId} suggestedDates={suggestedDates} />
        </Field>

        {error && <Text style={{ color: theme.danger, fontSize: 14 }}>{error}</Text>}

        <Button label={submitLabel} onPress={submit} loading={submitting} disabled={submitting} />

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
  switchRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  timeRow: { flexDirection: 'row', gap: Spacing.three },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two },
});
