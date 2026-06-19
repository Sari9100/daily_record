import { useQuery } from '@tanstack/react-query';
import { api } from './client';
import type { ApiResponse, TimelineResponse } from '@/domain/types';

export type TimelineParams = {
  /** 단일 날짜 YYYY-MM-DD */
  date?: string;
  /** 기간 시작 YYYY-MM-DD */
  from?: string;
  /** 기간 끝 YYYY-MM-DD */
  to?: string;
};

export async function fetchTimeline(params: TimelineParams): Promise<TimelineResponse> {
  const res = await api.get<ApiResponse<TimelineResponse>>('/timeline', { params });
  if (!res.data.data) {
    throw new Error(res.data.error?.message ?? '타임라인을 불러오지 못했습니다.');
  }
  return res.data.data;
}

export function useTimeline(params: TimelineParams) {
  return useQuery({
    queryKey: ['timeline', params],
    queryFn: () => fetchTimeline(params),
  });
}
