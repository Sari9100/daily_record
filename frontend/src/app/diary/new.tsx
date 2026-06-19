import { useRouter } from 'expo-router';

import { DiaryForm } from '@/components/DiaryForm';
import { useCreateDiary } from '@/api/diary';

export default function NewDiaryScreen() {
  const router = useRouter();
  const createMut = useCreateDiary();

  return (
    <DiaryForm
      submitting={createMut.isPending}
      submitLabel="저장"
      onSubmit={async (body) => {
        const created = await createMut.mutateAsync(body);
        // 저장 후 사진 추가를 위해 편집 화면으로 교체 이동
        router.replace({ pathname: '/diary/[id]', params: { id: created.id } });
      }}
    />
  );
}
