import { useState } from 'react';
import { View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { DiaryDetail } from '@/components/DiaryDetail';
import { DiaryForm } from '@/components/DiaryForm';
import { PhotoSection } from '@/components/PhotoSection';
import { EmptyState } from '@/components/ui';
import { useTheme } from '@/hooks/use-theme';
import { useCachedDiary, useDeleteDiary, useUpdateDiary } from '@/api/diary';
import { confirmAsync } from '@/lib/confirm';

/** 기록 클릭 시 먼저 보기 화면(DiaryDetail)이 뜨고, "수정" 버튼으로 편집 화면(DiaryForm)으로 전환. */
export default function DiaryDetailScreen() {
  const theme = useTheme();
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const numId = Number(id);

  const diary = useCachedDiary(numId);
  const updateMut = useUpdateDiary(numId);
  const deleteMut = useDeleteDiary();
  const [mode, setMode] = useState<'view' | 'edit'>('view');

  if (!diary) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.background }}>
        <EmptyState text="기록을 찾을 수 없습니다. 목록에서 다시 열어주세요." />
      </View>
    );
  }

  const remove = async () => {
    if (await confirmAsync('이 기록을 삭제할까요? (사진도 함께 삭제됩니다)')) {
      await deleteMut.mutateAsync(numId);
      router.back();
    }
  };

  if (mode === 'view') {
    return (
      <View style={{ flex: 1, backgroundColor: theme.background }}>
        <DiaryDetail diary={diary} onEdit={() => setMode('edit')} onDelete={remove} deleting={deleteMut.isPending} />
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
        setMode('view');
      }}
      onDelete={remove}>
      <PhotoSection diaryId={numId} initialPhotos={diary.photos} />
    </DiaryForm>
  );
}
