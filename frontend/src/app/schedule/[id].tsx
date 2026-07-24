import { View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { ScheduleForm } from '@/components/ScheduleForm';
import { EmptyState, SidePanel } from '@/components/ui';
import { useTheme } from '@/hooks/use-theme';
import { useCachedSchedule, useDeleteSchedule, useUpdateSchedule } from '@/api/schedule';
import { confirmAsync } from '@/lib/confirm';

export default function EditScheduleScreen() {
  const theme = useTheme();
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const numId = Number(id);

  const schedule = useCachedSchedule(numId);
  const updateMut = useUpdateSchedule(numId);
  const deleteMut = useDeleteSchedule();

  if (!schedule) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.background }}>
        <EmptyState text="일정을 찾을 수 없습니다. 목록에서 다시 열어주세요." />
      </View>
    );
  }

  return (
    <SidePanel title="일정 편집" onClose={() => router.back()}>
      <ScheduleForm
        initial={schedule}
        submitting={updateMut.isPending}
        deleting={deleteMut.isPending}
        submitLabel="수정"
        onSubmit={async (body) => {
          await updateMut.mutateAsync(body);
          router.back();
        }}
        onDelete={async () => {
          if (await confirmAsync('이 일정을 삭제할까요?')) {
            await deleteMut.mutateAsync(numId);
            router.back();
          }
        }}
      />
    </SidePanel>
  );
}
