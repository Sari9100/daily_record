import type { FamilyRole, Visibility } from './types';

export type ScheduleType = 'EVENT' | 'TODO' | 'REMINDER';
export type SyncStatus = 'SYNCED' | 'PENDING' | 'CONFLICT' | 'DELETED_REMOTE';

export type Schedule = {
  id: number;
  title: string;
  description: string | null;
  location: string | null;
  startedAt: string | null;
  endedAt: string | null;
  startDate: string | null;
  endDate: string | null;
  allDay: boolean;
  visibility: Visibility;
  scheduleType: ScheduleType;
  isDone: boolean;
  recurrenceRule: string | null;
  subjects: number[];
  participants: number[];
  collectionId: number | null;
  syncStatus: SyncStatus | null;
};

/** 일정 생성/수정 요청. 이원화: allDay=true→startDate(필수)/endDate, false→startedAt(필수)/endedAt. */
export type ScheduleCreate = {
  title: string;
  description?: string | null;
  location?: string | null;
  startedAt?: string | null;
  endedAt?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  allDay: boolean;
  visibility: Visibility;
  scheduleType: ScheduleType;
  recurrenceRule?: string | null;
  collectionId?: number | null;
  subjectPersonIds?: number[];
  participantPersonIds?: number[];
};

export type FamilyMember = {
  personId: number;
  name: string;
  role: FamilyRole;
  hasAccount: boolean;
};

export const SCHEDULE_TYPE_LABEL: Record<ScheduleType, string> = {
  EVENT: '일정',
  TODO: '할일',
  REMINDER: '리마인더',
};

export const SYNC_STATUS_LABEL: Record<SyncStatus, string> = {
  SYNCED: '구글',
  PENDING: '동기화 대기',
  CONFLICT: '충돌',
  DELETED_REMOTE: '구글삭제',
};
