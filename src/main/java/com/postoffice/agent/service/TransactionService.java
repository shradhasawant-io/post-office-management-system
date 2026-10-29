package com.postoffice.agent.service;

import com.postoffice.agent.dto.transaction.TransactionRequest;
import com.postoffice.agent.dto.transaction.TransactionResponse;

import java.util.List;

public interface TransactionService {

    TransactionResponse createTransaction(TransactionRequest request);

    TransactionResponse getTransactionById(Long transactionId);

    List<TransactionResponse> getAllTransactions();

    List<TransactionResponse> getTransactionsByAccountId(Long accountId);

    List<TransactionResponse> getTransactionsByAccountNumber(String accountNumber);

    List<TransactionResponse> getTransactionsByAgentId(Long agentId);
}