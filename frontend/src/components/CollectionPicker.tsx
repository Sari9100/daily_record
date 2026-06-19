import { ChipGroup, type ChipOption } from '@/components/ChipGroup';
import { useCollections } from '@/api/collection';

/** 묶음(컬렉션) 단일 선택 — "없음"(value 0 → null) 포함. */
export function CollectionPicker({
  value,
  onChange,
}: {
  value: number | null;
  onChange: (value: number | null) => void;
}) {
  const q = useCollections();
  const options: ChipOption<number>[] = [
    { value: 0, label: '없음' },
    ...(q.data ?? []).map((c) => ({ value: c.id, label: c.name })),
  ];
  return <ChipGroup options={options} value={value ?? 0} onChange={(v) => onChange(v === 0 ? null : v)} />;
}
