package com.familyos.ledger.entity;

/**
 * 거래 유형.
 * <ul>
 *   <li>EXPENSE — source_account 만 (나간 돈)</li>
 *   <li>INCOME — target_account 만 (들어온 돈)</li>
 *   <li>TRANSFER — source·target 모두 (서로 다름). 통계에서는 제외</li>
 * </ul>
 */
public enum TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER
}
