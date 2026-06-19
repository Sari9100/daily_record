/**
 * 백엔드 API 계약 타입 (docs/05). 응답은 {success, data, error} 래퍼.
 * boolean 은 서버 직렬화 규칙상 isXxx. family_id 는 클라가 보내지 않는다(토큰 기반).
 */

export type ApiResponse<T> = {
  success: boolean;
  data: T | null;
  error: ApiError | null;
};

export type ApiError = {
  code: string;
  message: string;
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type FamilyRole = 'PARENT' | 'CHILD';

/** 4단계 공개범위 (docs/06). */
export type Visibility = 'PRIVATE' | 'SHARED_PERSONAL' | 'PARENTS' | 'FAMILY';

// ---- 인증 ----

export type PersonSummary = {
  id: number;
  name: string;
  familyId: number;
  role: FamilyRole;
};

export type LoginResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  person: PersonSummary;
};

export type TokenResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
};

export type MeResponse = {
  person: { id: number; name: string };
  family: { id: number; name: string };
  role: FamilyRole;
};

/** 앱 내부 정규화 사용자(로그인/me 응답을 한 형태로). */
export type Me = {
  personId: number;
  name: string;
  familyId: number;
  familyName?: string;
  role: FamilyRole;
};

// ---- 타임라인 (docs/05) ----

export type TimelineItemType = 'TRANSACTION' | 'SCHEDULE' | 'DIARY' | 'PHOTO';

export type TimelineItem = {
  type: TimelineItemType;
  id: number;
  /** 로컬 "HH:mm" — 시각 없는 종일/날짜 항목은 null. */
  time: string | null;
  /** 마스킹/비공개 시 null 로 내려올 수 있음 → UI 에서 플레이스홀더 처리. */
  title: string | null;
  /** TRANSACTION 금액(그 외 null). */
  amount: number | null;
  thumbnailUrl: string | null;
};

export type TimelineDay = {
  /** YYYY-MM-DD */
  date: string;
  items: TimelineItem[];
};

export type TimelineResponse = {
  days: TimelineDay[];
};
