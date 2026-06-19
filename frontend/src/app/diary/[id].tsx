import { StyleSheet, Text, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { DiaryForm } from '@/components/DiaryForm';
import { PhotoSection } from '@/components/PhotoSection';
import { useCachedDiary, useDeleteDiary, useUpdateDiary } from '@/api/diary';
import { confirmAsync } from '@/lib/confirm';

export default function EditDiaryScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const numId = Number(id);

  const diary = useCachedDiary(numId);
  const updateMut = useUpdateDiary(numId);
  const deleteMut = useDeleteDiary();

  if (!diary) {
    return (
      <View style={styles.center}>
        <Text style={styles.notFound}>기록을 찾을 수 없습니다. 목록에서 다시 열어주세요.</Text>
      </View>
    );
  }

  return (
    <DiaryForm
      initial={diary}
      submitting={updateMut.isPending}
      deleting={deleteMut.isPending}
      submitLabel="수정"
      onSubmit={async (body) => {
        await updateMut.mutateAsync(body);
        router.back();
      }}
      onDelete={async () => {
        if (await confirmAsync('이 기록을 삭제할까요? (사진도 함께 삭제됩니다)')) {
          await deleteMut.mutateAsync(numId);
          router.back();
        }
      }}>
      <PhotoSection diaryId={numId} initialPhotos={diary.photos} />
    </DiaryForm>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, backgroundColor: '#fff' },
  notFound: { fontSize: 15, color: '#5f6368', textAlign: 'center' },
});
