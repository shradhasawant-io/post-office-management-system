package com.postoffice.agent.mapper;

import com.postoffice.agent.dto.transaction.TransactionRequest;
import com.postoffice.agent.dto.transaction.TransactionResponse;
import com.postoffice.agent.entity.Transaction;

public class TransactionMapper {

    public static Transaction toEntity(TransactionRequest request) {

        return Transaction.builder()
                .idempotencyKey(normalizeIdempotencyKey(request.getIdempotencyKey()))
                .transactionAmount(request.getTransactionAmount())
                .paymentMode(request.getPaymentMode())
                .remarks(request.getRemarks())
                .build();
    }

    private static String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return key.trim();
    }

    public static TransactionResponse toResponse(Transaction transaction) {

        return TransactionResponse.builder()
                .transactionNumber(transaction.getTransactionNumber())
                .accountNumber(transaction.getTransactionAccountId().getAccountNumber())
                .customerName(transaction.getTransactionAccountId().getCustomer().getName())
                .customerCode(transaction.getTransactionAccountId().getCustomer().getCustomerCode())
                .transactionDate(transaction.getTransactionDate())
                .transactionType(transaction.getTransactionType())
                .transactionAmount(transaction.getTransactionAmount())
                .paymentMode(transaction.getPaymentMode())
                .remarks(transaction.getRemarks())
                .balance(transaction.getBalance())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}