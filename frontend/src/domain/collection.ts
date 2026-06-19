export type Collection = {
  id: number;
  name: string;
  description: string | null;
  coverPhotoId: number | null;
  startedAt: string | null;
  endedAt: string | null;
};

export type CollectionCreate = {
  name: string;
  description?: string | null;
  startedAt?: string | null;
  endedAt?: string | null;
};
