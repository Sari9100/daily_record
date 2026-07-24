export type Collection = {
  id: number;
  name: string;
  description: string | null;
  coverPhotoId: number | null;
  startedAt: string | null;
  endedAt: string | null;
  tags: string[];
};

export type CollectionCreate = {
  name: string;
  description?: string | null;
  startedAt?: string | null;
  endedAt?: string | null;
  tagIds?: number[];
};

export type CollectionSummary = {
  totalIncome: number;
  totalExpense: number;
  transactionCount: number;
  scheduleCount: number;
  diaryCount: number;
  photoCount: number;
};
