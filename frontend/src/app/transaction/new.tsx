import { useRouter } from 'expo-router';

import { TransactionForm } from '@/components/TransactionForm';
import { useCreateTransaction } from '@/api/ledger';

export default function NewTransactionScreen() {
  const router = useRouter();
  const createMut = useCreateTransaction();

  return (
    <TransactionForm
      submitting={createMut.isPending}
      submitLabel="저장"
      onSubmit={async (body) => {
        await createMut.mutateAsync(body);
        router.back();
      }}
    />
  );
}
