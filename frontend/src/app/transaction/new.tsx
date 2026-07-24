import { useLocalSearchParams, useRouter } from 'expo-router';

import { TransactionForm } from '@/components/TransactionForm';
import { SidePanel } from '@/components/ui';
import { useCreateTransaction } from '@/api/ledger';

export default function NewTransactionScreen() {
  const router = useRouter();
  const { collectionId } = useLocalSearchParams<{ collectionId?: string }>();
  const createMut = useCreateTransaction();

  return (
    <SidePanel title="거래 입력" onClose={() => router.back()}>
      <TransactionForm
        initial={collectionId ? { collectionId: Number(collectionId) } : undefined}
        submitting={createMut.isPending}
        submitLabel="저장"
        onSubmit={async (body) => {
          await createMut.mutateAsync(body);
          router.back();
        }}
      />
    </SidePanel>
  );
}
