import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type { ApiResponse, FamilyRole } from '@/domain/types';

export type AddMemberBody = {
  name: string;
  role: FamilyRole;
  birthDate?: string | null;
  timezone?: string | null;
};

export function useAddMember() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: AddMemberBody) => {
      const res = await api.post<ApiResponse<unknown>>('/family/members', body);
      if (!res.data.success) throw new Error(res.data.error?.message ?? '구성원 추가에 실패했습니다.');
      return res.data.data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['family'] }),
  });
}

export function useCreateMemberAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ personId, loginId, password }: { personId: number; loginId: string; password: string }) => {
      const res = await api.post<ApiResponse<unknown>>(`/family/members/${personId}/account`, { loginId, password });
      if (!res.data.success) throw new Error(res.data.error?.message ?? '계정 생성에 실패했습니다.');
      return res.data.data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['family'] }),
  });
}
