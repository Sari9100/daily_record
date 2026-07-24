import { View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { TransactionForm, type TransactionInitial } from '@/components/TransactionForm';
import { EmptyState, SidePanel } from '@/components/ui';
import { useTheme } from '@/hooks/use-theme';
import {
  useCachedTransaction,
  useDeleteTransaction,
  useUpdateTransaction,
} from '@/api/ledger';
import { confirmAsync } from '@/lib/confirm';

export default function EditTransactionScreen() {
  const theme = useTheme();
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const numId = Number(id);

  const t = useCachedTransaction(numId);
  const updateMut = useUpdateTransaction(numId);
  const deleteMut = useDeleteTransaction();

  if (!t) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.background }}>
        <EmptyState text="거래를 찾을 수 없습니다. 목록에서 다시 열어주세요." />
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
    collectionId: t.collectionId,
  };

  return (
    <SidePanel title="거래 편집" onClose={() => router.back()}>
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
    </SidePanel>
  );
}
