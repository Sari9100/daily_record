import { StyleSheet, Text, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { ScheduleForm } from '@/components/ScheduleForm';
import { useCachedSchedule, useDeleteSchedule, useUpdateSchedule } from '@/api/schedule';
import { confirmAsync } from '@/lib/confirm';

export default function EditScheduleScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const numId = Number(id);

  const schedule = useCachedSchedule(numId);
  const updateMut = useUpdateSchedule(numId);
  const deleteMut = useDeleteSchedule();

  if (!schedule) {
    return (
      <View style={styles.center}>
        <Text style={styles.notFound}>일정을 찾을 수 없습니다. 목록에서 다시 열어주세요.</Text>
      </View>
    );
  }

  return (
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
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, backgroundColor: '#fff' },
  notFound: { fontSize: 15, color: '#5f6368', textAlign: 'center' },
});
