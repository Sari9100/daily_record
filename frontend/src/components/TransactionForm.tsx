import { type ReactNode, useEffect, useMemo, useState } from 'react';
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

import { ChipGroup, type ChipOption } from '@/components/ChipGroup';
import { useAccounts, useCategories } from '@/api/ledger';
import type { TransactionCreate, TransactionType } from '@/domain/ledger';
import type { Visibility } from '@/domain/types';

const TYPE_OPTIONS: ChipOption<TransactionType>[] = [
  { value: 'EXPENSE', label: '지출' },
  { value: 'INCOME', label: '수입' },
  { value: 'TRANSFER', label: '이체' },
];

const VISIBILITY_OPTIONS: ChipOption<Visibility>[] = [
  { value: 'PRIVATE', label: '개인' },
  { value: 'PARENTS', label: '부모공유' },
  { value: 'FAMILY', label: '가족' },
];

export type TransactionInitial = {
  type?: TransactionType;
  amount?: string;
  sourceId?: number | null;
  targetId?: number | null;
  categoryId?: number | null;
  visibility?: Visibility;
  memo?: string;
  occurredAt?: string;
};

export function TransactionForm({
  initial,
  submitting,
  submitLabel,
  onSubmit,
  onDelete,
  deleting,
}: {
  initial?: TransactionInitial;
  submitting: boolean;
  submitLabel: string;
  onSubmit: (body: TransactionCreate) => Promise<void>;
  onDelete?: () => void;
  deleting?: boolean;
}) {
  const [type, setType] = useState<TransactionType>(initial?.type ?? 'EXPENSE');
  const [amount, setAmount] = useState(initial?.amount ?? '');
  const [sourceId, setSourceId] = useState<number | null>(initial?.sourceId ?? null);
  const [targetId, setTargetId] = useState<number | null>(initial?.targetId ?? null);
  const [categoryId, setCategoryId] = useState<number | null>(initial?.categoryId ?? null);
  const [visibility, setVisibility] = useState<Visibility>(initial?.visibility ?? 'PRIVATE');
  const [memo, setMemo] = useState(initial?.memo ?? '');
  const [formError, setFormError] = useState<string | null>(null);
  const [occurredAt] = useState(() => initial?.occurredAt ?? new Date().toISOString());
  // 유형이 사용자 조작으로 바뀌었는지 — 초기 prefill 시점엔 계좌/카테고리를 지우지 않도록 가드
  const [typeTouched, setTypeTouched] = useState(false);

  const accountsQ = useAccounts();
  const categoriesQ = useCategories(type === 'TRANSFER' ? undefined : type);

  useEffect(() => {
    if (!typeTouched) return;
    setCategoryId(null);
    if (type === 'EXPENSE') setTargetId(null);
    if (type === 'INCOME') setSourceId(null);
  }, [type, typeTouched]);

  const accountOptions: ChipOption<number>[] = useMemo(
    () => (accountsQ.data ?? []).map((a) => ({ value: a.id, label: a.name })),
    [accountsQ.data],
  );
  const categoryOptions: ChipOption<number>[] = useMemo(
    () => (categoriesQ.data ?? []).map((c) => ({ value: c.id, label: c.name })),
    [categoriesQ.data],
  );

  const validate = (): string | null => {
    const value = Number(amount.replace(/,/g, ''));
    if (!amount || Number.isNaN(value) || value <= 0) return '금액을 올바르게 입력하세요.';
    if (type === 'EXPENSE' && sourceId == null) return '출금 계좌를 선택하세요.';
    if (type === 'INCOME' && targetId == null) return '입금 계좌를 선택하세요.';
    if (type === 'TRANSFER') {
      if (sourceId == null || targetId == null) return '출금·입금 계좌를 모두 선택하세요.';
      if (sourceId === targetId) return '이체는 출금·입금 계좌가 달라야 합니다.';
    }
    return null;
  };

  const submit = async () => {
    const err = validate();
    setFormError(err);
    if (err) return;
    try {
      await onSubmit({
        transactionType: type,
        amount: Number(amount.replace(/,/g, '')),
        currency: 'KRW',
        sourceAccountId: type === 'INCOME' ? null : sourceId,
        targetAccountId: type === 'EXPENSE' ? null : targetId,
        categoryId: type === 'TRANSFER' ? null : categoryId,
        visibility,
        occurredAt,
        memo: memo.trim() ? memo.trim() : null,
      });
    } catch (e) {
      setFormError(e instanceof Error ? e.message : '저장에 실패했습니다.');
    }
  };

  const changeType = (t: TransactionType) => {
    setTypeTouched(true);
    setType(t);
  };

  const showSource = type === 'EXPENSE' || type === 'TRANSFER';
  const showTarget = type === 'INCOME' || type === 'TRANSFER';

  return (
    <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <Field label="유형">
          <ChipGroup options={TYPE_OPTIONS} value={type} onChange={changeType} />
        </Field>

        <Field label="금액">
          <TextInput
            style={styles.input}
            value={amount}
            onChangeText={setAmount}
            keyboardType="number-pad"
            placeholder="0"
            placeholderTextColor="#9aa0a6"
          />
        </Field>

        {showSource && (
          <Field label={type === 'TRANSFER' ? '출금 계좌' : '결제 계좌'}>
            <ChipGroup
              options={accountOptions}
              value={sourceId}
              onChange={setSourceId}
              emptyText={accountsQ.isLoading ? '불러오는 중…' : '계좌가 없습니다. 계좌를 먼저 추가하세요.'}
            />
          </Field>
        )}

        {showTarget && (
          <Field label="입금 계좌">
            <ChipGroup
              options={accountOptions}
              value={targetId}
              onChange={setTargetId}
              emptyText={accountsQ.isLoading ? '불러오는 중…' : '계좌가 없습니다.'}
            />
          </Field>
        )}

        {type !== 'TRANSFER' && (
          <Field label="카테고리 (선택)">
            <ChipGroup
              options={categoryOptions}
              value={categoryId}
              onChange={setCategoryId}
              emptyText={categoriesQ.isLoading ? '불러오는 중…' : '카테고리가 없습니다.'}
            />
          </Field>
        )}

        <Field label="공개 범위">
          <ChipGroup options={VISIBILITY_OPTIONS} value={visibility} onChange={setVisibility} />
        </Field>

        <Field label="메모 (선택)">
          <TextInput
            style={styles.input}
            value={memo}
            onChangeText={setMemo}
            placeholder="메모"
            placeholderTextColor="#9aa0a6"
          />
        </Field>

        {formError && <Text style={styles.error}>{formError}</Text>}

        <Pressable
          style={[styles.btn, styles.save, submitting && styles.disabled]}
          disabled={submitting}
          onPress={submit}>
          {submitting ? <ActivityIndicator color="#fff" /> : <Text style={styles.saveText}>{submitLabel}</Text>}
        </Pressable>

        {onDelete && (
          <Pressable
            style={[styles.btn, styles.delete, deleting && styles.disabled]}
            disabled={deleting}
            onPress={onDelete}>
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
  container: { padding: 20, gap: 20 },
  field: { gap: 8 },
  label: { fontSize: 13, fontWeight: '600', color: '#3c4043' },
  input: {
    borderWidth: 1,
    borderColor: '#dadce0',
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 16,
    color: '#202124',
  },
  error: { color: '#d93025', fontSize: 14 },
  btn: { borderRadius: 10, paddingVertical: 14, alignItems: 'center' },
  save: { backgroundColor: '#1a73e8' },
  saveText: { color: '#fff', fontSize: 16, fontWeight: '600' },
  delete: { borderWidth: 1, borderColor: '#d93025' },
  deleteText: { color: '#d93025', fontSize: 16, fontWeight: '600' },
  disabled: { opacity: 0.6 },
});
