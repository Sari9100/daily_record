import { StyleSheet, Text, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { TransactionForm, type TransactionInitial } from '@/components/TransactionForm';
import {
  useCachedTransaction,
  useDeleteTransaction,
  useUpdateTransaction,
} from '@/api/ledger';
import { confirmAsync } from '@/lib/confirm';

export default function EditTransactionScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const numId = Number(id);

  const t = useCachedTransaction(numId);
  const updateMut = useUpdateTransaction(numId);
  const deleteMut = useDeleteTransaction();

  if (!t) {
    return (
      <View style={styles.center}>
        <Text style={styles.notFound}>거래를 찾을 수 없습니다. 목록에서 다시 열어주세요.</Text>
      </View>
    );
  }

  const initial: TransactionInitial = {
    type: t.transactionType,
    amount: String(t.amount),
    sourceId: t.sourceAccount?.id ?? null,
    targetId: t.targetAccount?.id ?? null,
    categoryId: t.category?.id ?? null,
    visibility: t.visibility,
    memo: t.memo ?? '',
    occurredAt: t.occurredAt,
  };

  return (
    <TransactionForm
      initial={initial}
      submitting={updateMut.isPending}
      deleting={deleteMut.isPending}
      submitLabel="수정"
      onSubmit={async (body) => {
        await updateMut.mutateAsync(body);
        router.back();
      }}
      onDelete={async () => {
        if (await confirmAsync('이 거래를 삭제할까요?')) {
          await deleteMut.mutateAsync(numId);
          router.back();
        }
      }}
    />
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, backgroundColor: '#fff' },
  notFound: { fontSize: 15, color: '#5f6368', textAlign: 'center' },
});
