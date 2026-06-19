import type { Visibility } from './types';

export type TransactionType = 'INCOME' | 'EXPENSE' | 'TRANSFER';
export type SettlementStatus = 'NONE' | 'PENDING' | 'SETTLED';
export type CategoryType = 'INCOME' | 'EXPENSE';

export type AccountRef = { id: number; name: string };
export type CategoryRef = { id: number; name: string };

export type Transaction = {
  id: number;
  transactionType: TransactionType;
  amount: number;
  currency: string;
  category: CategoryRef | null;
  /** 소유자 본인이 아니면 마스킹(null) — UI 에서 "비공개" 칩. */
  sourceAccount: AccountRef | null;
  targetAccount: AccountRef | null;
  subjectPersonId: number | null;
  visibility: Visibility;
  settlementStatus: SettlementStatus;
  occurredAt: string;
  memo: string | null;
  collectionId: number | null;
  tags: string[];
};

export type Account = {
  id: number;
  name: string;
  assetType: string;
  ownerType: string;
  ownerPersonId: number | null;
  visibility: Visibility;
};

export type Category = {
  id: number;
  name: string;
  type: CategoryType;
  parentId: number | null;
  isSystem: boolean;
};

/** 거래 생성 요청 (서버 결정값 family_id/settlement_status 제외). */
export type TransactionCreate = {
  transactionType: TransactionType;
  amount: number;
  currency?: string;
  sourceAccountId?: number | null;
  targetAccountId?: number | null;
  categoryId?: number | null;
  subjectPersonId?: number | null;
  visibility: Visibility;
  occurredAt: string;
  memo?: string | null;
  collectionId?: number | null;
  tagIds?: number[];
};

export const TRANSACTION_TYPE_LABEL: Record<TransactionType, string> = {
  EXPENSE: '지출',
  INCOME: '수입',
  TRANSFER: '이체',
};

export type AssetType = 'BANK' | 'SECURITIES' | 'CASH';
export type AccountOwnerType = 'PERSON' | 'FAMILY';

export type AccountCreate = {
  name: string;
  assetType: AssetType;
  ownerType: AccountOwnerType;
  ownerPersonId?: number | null;
  visibility: Visibility;
};

export type CategoryCreate = {
  name: string;
  type: CategoryType;
  parentId?: number | null;
};

export const ASSET_TYPE_LABEL: Record<AssetType, string> = {
  BANK: '은행',
  SECURITIES: '증권',
  CASH: '현금',
};

export const CATEGORY_TYPE_LABEL: Record<CategoryType, string> = {
  EXPENSE: '지출',
  INCOME: '수입',
};
