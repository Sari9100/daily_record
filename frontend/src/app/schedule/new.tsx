import { useLocalSearchParams, useRouter } from 'expo-router';

import { ScheduleForm } from '@/components/ScheduleForm';
import { SidePanel } from '@/components/ui';
import { useCreateSchedule } from '@/api/schedule';

export default function NewScheduleScreen() {
  const router = useRouter();
  const { collectionId } = useLocalSearchParams<{ collectionId?: string }>();
  const createMut = useCreateSchedule();

  return (
    <SidePanel title="일정 추가" onClose={() => router.back()}>
      <ScheduleForm
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
