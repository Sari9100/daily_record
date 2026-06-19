import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type { ApiResponse, PageResponse } from '@/domain/types';
import type {
  Account,
  Category,
  CategoryType,
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
