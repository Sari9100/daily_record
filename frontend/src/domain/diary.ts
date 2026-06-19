import type { Visibility } from './types';

export type DiaryPhoto = {
  id: number;
  url: string | null;
  width: number | null;
  height: number | null;
  takenAt: string | null;
};

export type Diary = {
  id: number;
  title: string | null;
  content: string;
  visibility: Visibility;
  recordedAt: string;
  recordedOn: string; // YYYY-MM-DD
  subjects: number[];
  collectionId: number | null;
  tags: string[];
  photos: DiaryPhoto[];
};

/** 일상기록 생성/수정. recordedOn(날짜) 필수. recordedAt(시각) 선택. */
export type DiaryCreate = {
  title?: string | null;
  content: string;
  visibility: Visibility;
  recordedOn: string;
  recordedAt?: string | null;
  collectionId?: number | null;
  subjectPersonIds?: number[];
  tagIds?: number[];
};
