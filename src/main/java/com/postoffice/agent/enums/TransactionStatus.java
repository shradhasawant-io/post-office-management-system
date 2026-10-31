package com.postoffice.agent.enums;

/**
 * Lifecycle status of a transaction record.
 * This is intentionally separate from TransactionType (DEPOSIT/WITHDRAW).
 */
public enum TransactionStatus {
    ACTIVE,
    INACTIVE
}
