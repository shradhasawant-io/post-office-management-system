
package com.postoffice.agent.controller;

import com.postoffice.agent.dto.transaction.TransactionRequest;
import com.postoffice.agent.dto.transaction.TransactionResponse;
import com.postoffice.agent.service.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // Create a deposit
    @PostMapping("/deposit")
    @PreAuthorize("hasRole('AGENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createDeposit(@RequestBody TransactionRequest request) {
        return transactionService.createTransaction(request);
    }

    // Get a transaction by its database ID
    @GetMapping("/{transactionId}")
    @PreAuthorize("hasRole('AGENT')")
    public TransactionResponse getTransactionById(@PathVariable Long transactionId) {
        return transactionService.getTransactionById(transactionId);
    }

    // Get all transactions - ADMIN ONLY
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<TransactionResponse> getAllTransactions() {
        return transactionService.getAllTransactions();
    }

    // Get transaction history for an account
    @GetMapping("/account/{transactionAccountId}")
    @PreAuthorize("hasRole('AGENT')")
    public List<TransactionResponse> getTransactionsByAccountId(@PathVariable("transactionAccountId") Long accountId) {
        return transactionService.getTransactionsByAccountId(accountId);
    }

    // Get transaction by agent
    @GetMapping("/agents/{agentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TransactionResponse>> getTransactionsByAgent(@PathVariable Long agentId) {
        return ResponseEntity.ok(transactionService.getTransactionsByAgentId(agentId));
    }
}
