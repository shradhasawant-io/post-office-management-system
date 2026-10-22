
package com.postoffice.agent.mapper;

import com.postoffice.agent.dto.transaction.TransactionRequest;
import com.postoffice.agent.dto.transaction.TransactionResponse;
import com.postoffice.agent.entity.Transaction;

public class TransactionMapper {

    public static Transaction toEntity(TransactionRequest request) {

        return Transaction.builder()
                .transactionAmount(request.getTransactionAmount())
                .paymentMode(request.getPaymentMode())
                .remarks(request.getRemarks())
                .build();
    }

    public static TransactionResponse toResponse(Transaction transaction) {

        return TransactionResponse.builder()
                .transactionId(transaction.getTransactionId())
                .transactionAccountId(transaction.getTransactionAccountId().getAccountId())
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
