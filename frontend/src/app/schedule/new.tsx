import { useRouter } from 'expo-router';

import { ScheduleForm } from '@/components/ScheduleForm';
import { useCreateSchedule } from '@/api/schedule';

export default function NewScheduleScreen() {
  const router = useRouter();
  const createMut = useCreateSchedule();

  return (
    <ScheduleForm
      submitting={createMut.isPending}
      submitLabel="저장"
      onSubmit={async (body) => {
        await createMut.mutateAsync(body);
        router.back();
      }}
    />
  );
}
