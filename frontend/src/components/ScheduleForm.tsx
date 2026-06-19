import { type ReactNode, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from 'react-native';
import { format, parseISO } from 'date-fns';

import { ChipGroup, type ChipOption } from '@/components/ChipGroup';
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
  initial?: Schedule;
  submitting: boolean;
  submitLabel: string;
  onSubmit: (body: ScheduleCreate) => Promise<void>;
  onDelete?: () => void;
  deleting?: boolean;
}) {
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
  const [error, setError] = useState<string | null>(null);

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
    <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <Field label="제목">
          <TextInput style={styles.input} value={title} onChangeText={setTitle} placeholder="제목" placeholderTextColor="#9aa0a6" />
        </Field>

        <Field label="유형">
          <ChipGroup options={TYPE_OPTIONS} value={type} onChange={setType} />
        </Field>

        <View style={styles.switchRow}>
          <Text style={styles.label}>종일</Text>
          <Switch value={allDay} onValueChange={setAllDay} />
        </View>

        {allDay ? (
          <>
            <Field label="시작 날짜 (YYYY-MM-DD)">
              <TextInput style={styles.input} value={startDate} onChangeText={setStartDate} placeholder="2026-06-20" placeholderTextColor="#9aa0a6" autoCapitalize="none" />
            </Field>
            <Field label="종료 날짜 (선택)">
              <TextInput style={styles.input} value={endDate} onChangeText={setEndDate} placeholder="비우면 하루 일정" placeholderTextColor="#9aa0a6" autoCapitalize="none" />
            </Field>
          </>
        ) : (
          <>
            <Field label="날짜 (YYYY-MM-DD)">
              <TextInput style={styles.input} value={date} onChangeText={setDate} placeholder="2026-06-20" placeholderTextColor="#9aa0a6" autoCapitalize="none" />
            </Field>
            <View style={styles.timeRow}>
              <View style={styles.flex}>
                <Field label="시작 (HH:mm)">
                  <TextInput style={styles.input} value={startTime} onChangeText={setStartTime} placeholder="09:00" placeholderTextColor="#9aa0a6" />
                </Field>
              </View>
              <View style={styles.flex}>
                <Field label="종료 (선택)">
                  <TextInput style={styles.input} value={endTime} onChangeText={setEndTime} placeholder="10:00" placeholderTextColor="#9aa0a6" />
                </Field>
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
              {(membersQ.data ?? []).map((m) => {
                const on = subjects.has(m.personId);
                return (
                  <Pressable
                    key={m.personId}
                    onPress={() => toggleSubject(m.personId)}
                    style={[styles.chip, on && styles.chipOn]}>
                    <Text style={[styles.chipText, on && styles.chipTextOn]}>{m.name}</Text>
                  </Pressable>
                );
              })}
            </View>
          </Field>
        )}

        <Field label="장소 (선택)">
          <TextInput style={styles.input} value={location} onChangeText={setLocation} placeholder="장소" placeholderTextColor="#9aa0a6" />
        </Field>

        <Field label="메모 (선택)">
          <TextInput style={styles.input} value={description} onChangeText={setDescription} placeholder="메모" placeholderTextColor="#9aa0a6" />
        </Field>

        {error && <Text style={styles.error}>{error}</Text>}

        <Pressable style={[styles.btn, styles.save, submitting && styles.disabled]} disabled={submitting} onPress={submit}>
          {submitting ? <ActivityIndicator color="#fff" /> : <Text style={styles.saveText}>{submitLabel}</Text>}
        </Pressable>

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
  switchRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  timeRow: { flexDirection: 'row', gap: 12 },
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
