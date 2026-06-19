import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type { ApiResponse, PageResponse } from '@/domain/types';
import type {
  Account,
  AccountCreate,
  Category,
  CategoryCreate,
  CategoryType,
  Statistics,
  Transaction,
  TransactionCreate,
  TransactionType,
} from '@/domain/ledger';

export type TransactionListParams = {
  from?: string;
  to?: string;
  type?: TransactionType;
  categoryId?: number;
  scope?: string;
  page?: number;
  size?: number;
};

async function fetchTransactions(params: TransactionListParams): Promise<PageResponse<Transaction>> {
  const res = await api.get<ApiResponse<PageResponse<Transaction>>>('/transactions', { params });
  if (!res.data.data) {
    throw new Error(res.data.error?.message ?? '거래를 불러오지 못했습니다.');
  }
  return res.data.data;
}

export function useTransactions(params: TransactionListParams) {
  return useQuery({
    queryKey: ['transactions', params],
    queryFn: () => fetchTransactions(params),
  });
}

/** 단건 GET 엔드포인트가 없어, 편집 화면은 목록 쿼리 캐시에서 거래를 찾아 prefill 한다. */
export function useCachedTransaction(id: number): Transaction | undefined {
  const qc = useQueryClient();
  const queries = qc.getQueriesData<PageResponse<Transaction>>({ queryKey: ['transactions'] });
  for (const [, data] of queries) {
    const found = data?.content.find((t) => t.id === id);
    if (found) return found;
  }
  return undefined;
}

export type StatisticsParams = { from?: string; to?: string; scope?: string };

async function fetchStatistics(params: StatisticsParams): Promise<Statistics> {
  const res = await api.get<ApiResponse<Statistics>>('/transactions/statistics', { params });
  if (!res.data.data) {
    throw new Error(res.data.error?.message ?? '통계를 불러오지 못했습니다.');
  }
  return res.data.data;
}

export function useStatistics(params: StatisticsParams) {
  return useQuery({
    queryKey: ['statistics', params],
    queryFn: () => fetchStatistics(params),
  });
}

async function fetchAccounts(): Promise<Account[]> {
  const res = await api.get<ApiResponse<Account[]>>('/accounts');
  return res.data.data ?? [];
}

export function useAccounts() {
  return useQuery({ queryKey: ['accounts'], queryFn: fetchAccounts });
}

async function fetchCategories(type?: CategoryType): Promise<Category[]> {
  const res = await api.get<ApiResponse<Category[]>>('/categories', {
    params: type ? { type } : undefined,
  });
  return res.data.data ?? [];
}

export function useCategories(type?: CategoryType) {
  return useQuery({ queryKey: ['categories', type ?? 'ALL'], queryFn: () => fetchCategories(type) });
}

async function createTransaction(body: TransactionCreate): Promise<Transaction> {
  const res = await api.post<ApiResponse<Transaction>>('/transactions', body);
  if (!res.data.data) {
    throw new Error(res.data.error?.message ?? '거래 저장에 실패했습니다.');
  }
  return res.data.data;
}

export function useCreateTransaction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: createTransaction,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['transactions'] });
      qc.invalidateQueries({ queryKey: ['timeline'] });
    },
  });
}

async function updateTransaction(id: number, body: TransactionCreate): Promise<Transaction> {
  const res = await api.put<ApiResponse<Transaction>>(`/transactions/${id}`, body);
  if (!res.data.data) {
    throw new Error(res.data.error?.message ?? '거래 수정에 실패했습니다.');
  }
  return res.data.data;
}

export function useUpdateTransaction(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: TransactionCreate) => updateTransaction(id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['transactions'] });
      qc.invalidateQueries({ queryKey: ['timeline'] });
    },
  });
}

export function useDeleteTransaction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.delete(`/transactions/${id}`).then(() => undefined),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['transactions'] });
      qc.invalidateQueries({ queryKey: ['timeline'] });
    },
  });
}

// ---- 계좌 CRUD ----

export function useCreateAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: AccountCreate) => {
      const res = await api.post<ApiResponse<Account>>('/accounts', body);
      if (!res.data.data) throw new Error(res.data.error?.message ?? '계좌 저장에 실패했습니다.');
      return res.data.data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['accounts'] }),
  });
}

export function useUpdateAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, body }: { id: number; body: AccountCreate }) => {
      const res = await api.put<ApiResponse<Account>>(`/accounts/${id}`, body);
      if (!res.data.data) throw new Error(res.data.error?.message ?? '계좌 수정에 실패했습니다.');
      return res.data.data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['accounts'] }),
  });
}

export function useDeleteAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.delete(`/accounts/${id}`).then(() => undefined),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['accounts'] }),
  });
}

// ---- 카테고리 CRUD ----

export function useCreateCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CategoryCreate) => {
      const res = await api.post<ApiResponse<Category>>('/categories', body);
      if (!res.data.data) throw new Error(res.data.error?.message ?? '카테고리 저장에 실패했습니다.');
      return res.data.data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['categories'] }),
  });
}

export function useUpdateCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, body }: { id: number; body: CategoryCreate }) => {
      const res = await api.put<ApiResponse<Category>>(`/categories/${id}`, body);
      if (!res.data.data) throw new Error(res.data.error?.message ?? '카테고리 수정에 실패했습니다.');
      return res.data.data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['categories'] }),
  });
}

export function useDeleteCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.delete(`/categories/${id}`).then(() => undefined),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['categories'] }),
  });
}
