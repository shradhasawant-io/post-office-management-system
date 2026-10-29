package com.postoffice.agent.service.serviceImpl;

import com.postoffice.agent.dto.transaction.TransactionRequest;
import com.postoffice.agent.dto.transaction.TransactionResponse;
import com.postoffice.agent.entity.Account;
import com.postoffice.agent.entity.Customer;
import com.postoffice.agent.entity.Transaction;
import com.postoffice.agent.enums.AccountStatus;
import com.postoffice.agent.enums.TransactionType;
import com.postoffice.agent.exception.BusinessException;
import com.postoffice.agent.exception.ResourceNotFoundException;
import com.postoffice.agent.mapper.TransactionMapper;
import com.postoffice.agent.repository.AccountRepository;
import com.postoffice.agent.repository.TransactionRepository;
import com.postoffice.agent.security.AuthenticatedAgentService;
import com.postoffice.agent.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AuthenticatedAgentService authenticatedAgentService;

    public TransactionServiceImpl(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            AuthenticatedAgentService authenticatedAgentService) {

        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.authenticatedAgentService = authenticatedAgentService;
    }

    // Create a deposit transaction. The account lock serializes balance updates.
    @Override
    @Transactional
    public TransactionResponse createTransaction(TransactionRequest request) {

        if (request == null || request.getTransactionAccountId() == null) {
            throw new BusinessException("Account ID is required");
        }

        if (request.getTransactionAmount() == null
                || request.getTransactionAmount().signum() <= 0) {
            throw new BusinessException("Transaction amount must be greater than zero");
        }

        Long authenticatedAgentId = authenticatedAgentService.getAuthenticatedAgentId();
        String idempotencyKey = normalizeIdempotencyKey(request.getIdempotencyKey());

        // Fast path for retries where the original request has already committed.
        Transaction existing = findMatchingIdempotentTransaction(idempotencyKey, request);
        if (existing != null) {
            validateAccountOwnership(existing.getTransactionAccountId(), authenticatedAgentId);
            return toResponseWithInstallmentCount(existing);
        }

        Account account = accountRepository.findByIdForUpdate(request.getTransactionAccountId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found with id: " + request.getTransactionAccountId()));

        validateAccountOwnership(account, authenticatedAgentId);

        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException("Transaction cannot be performed on an inactive account");
        }

        // Re-check after acquiring the account lock: two concurrent retries can both
        // pass the fast path before either request commits.
        existing = findMatchingIdempotentTransaction(idempotencyKey, request);
        if (existing != null) {
            validateAccountOwnership(existing.getTransactionAccountId(), authenticatedAgentId);
            return toResponseWithInstallmentCount(existing);
        }

        account.setBalance(account.getBalance().add(request.getTransactionAmount()));
        accountRepository.save(account);

        Transaction transaction = TransactionMapper.toEntity(request);
        transaction.setTransactionAccountId(account);
        transaction.setTransactionType(TransactionType.DEPOSIT);
        transaction.setTransactionAmount(request.getTransactionAmount());
        transaction.setBalance(account.getBalance());

        if (transaction.getTransactionDate() == null) {
            transaction.setTransactionDate(java.time.LocalDate.now());
        }

        Transaction savedTransaction = transactionRepository.saveAndFlush(transaction);

        log.info(
                "Transaction {} created by Agent {} for Account {}. Amount: {}, New Balance: {}",
                savedTransaction.getTransactionId(),
                authenticatedAgentId,
                account.getAccountId(),
                request.getTransactionAmount(),
                account.getBalance()
        );

        return toResponseWithInstallmentCount(savedTransaction);
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim();
        if (normalized.length() > 100) {
            throw new BusinessException("Idempotency key must not exceed 100 characters");
        }
        return normalized;
    }

    private Transaction findMatchingIdempotentTransaction(
            String idempotencyKey,
            TransactionRequest request) {

        if (idempotencyKey == null) {
            return null;
        }

        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isEmpty()) {
            return null;
        }

        Transaction transaction = existing.get();
        boolean sameAccount = transaction.getTransactionAccountId() != null
                && transaction.getTransactionAccountId().getAccountId()
                .equals(request.getTransactionAccountId());
        boolean sameAmount = transaction.getTransactionAmount() != null
                && transaction.getTransactionAmount().compareTo(request.getTransactionAmount()) == 0;
        boolean samePaymentMode = transaction.getPaymentMode() == request.getPaymentMode();
        boolean sameRemarks = java.util.Objects.equals(
                normalizeRemarks(transaction.getRemarks()),
                normalizeRemarks(request.getRemarks()));

        if (!sameAccount || !sameAmount || !samePaymentMode || !sameRemarks) {
            throw new BusinessException(
                    "This idempotency key was already used for a different deposit request");
        }

        return transaction;
    }

    private String normalizeRemarks(String remarks) {
        return remarks == null || remarks.isBlank() ? null : remarks.trim();
    }

    private TransactionResponse toResponseWithInstallmentCount(Transaction transaction) {
        TransactionResponse response = TransactionMapper.toResponse(transaction);
        if (transaction.getTransactionType() == TransactionType.DEPOSIT
                && transaction.getTransactionAccountId() != null
                && transaction.getTransactionId() != null) {
            response.setInstallmentCount(
                    transactionRepository
                            .countByTransactionAccountId_AccountIdAndTransactionTypeAndTransactionIdLessThanEqual(
                                    transaction.getTransactionAccountId().getAccountId(),
                                    TransactionType.DEPOSIT,
                                    transaction.getTransactionId()
                            )
            );
        } else {
            response.setInstallmentCount(null);
        }
        return response;
    }

    //Get Transaction By ID
    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(Long transactionId) {

        Long authenticatedAgentId = authenticatedAgentService.getAuthenticatedAgentId();

        log.info("Agent {} attempting to access Transaction {}", authenticatedAgentId, transactionId);

        Optional<Transaction> transactionOptional = transactionRepository.findById(transactionId);

        if (transactionOptional.isEmpty()) {
            log.warn("Transaction {} not found", transactionId);

            throw new ResourceNotFoundException("Transaction not found with id: " + transactionId);
        }


        Transaction transaction = transactionOptional.get();

        // Ownership Check
        Account account = transaction.getTransactionAccountId();

        validateAccountOwnership(account, authenticatedAgentId);

        return toResponseWithInstallmentCount(transaction);
    }

    // Get all transactions - ADMIN ACCESS
    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getAllTransactions() {

        List<Transaction> transactions = transactionRepository.findAll();

        return transactions.stream()
                .map(this::toResponseWithInstallmentCount)
                .toList();
    }

    //Get Transaction For Account
    @Override
    public List<TransactionResponse> getTransactionsByAccountId(Long accountId) {
        Long authenticatedAgentId =
                authenticatedAgentService.getAuthenticatedAgentId();

        log.info(
                "Agent {} requesting transaction history for Account {}",
                authenticatedAgentId,
                accountId
        );

        // 1. Find Account
        Optional<Account> accountOptional =
                accountRepository.findById(accountId);

        if (accountOptional.isEmpty()) {
            log.warn("Account {} not found", accountId);

            throw new ResourceNotFoundException("Account not found with id: " + accountId);
        }

        Account account = accountOptional.get();

        // 2. Ownership Check
        validateAccountOwnership(account, authenticatedAgentId);

        // 3. Fetch only this account's transactions in sequence order.
        List<Transaction> transactions =
                transactionRepository.findByTransactionAccountId_AccountIdOrderByTransactionIdAsc(accountId);

        List<TransactionResponse> responses = transactions.stream()
                .map(this::toResponseWithInstallmentCount)
                .toList();

        log.info("Returning {} transactions for Account {} to Agent {}", responses.size(), accountId, authenticatedAgentId);
        return responses;
    }


    // Get transaction history by customer-facing account number.
    // The account lookup is scoped to the authenticated agent to prevent cross-agent access.
    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new BusinessException("Account number is required");
        }

        Long authenticatedAgentId = authenticatedAgentService.getAuthenticatedAgentId();
        String normalizedAccountNumber = accountNumber.trim();

        Account account = accountRepository
                .findByAccountNumberAndCustomer_Agent_AgentId(
                        normalizedAccountNumber,
                        authenticatedAgentId
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found for the provided account number"
                ));

        List<Transaction> transactions = transactionRepository
                .findByTransactionAccountId_AccountIdOrderByTransactionIdAsc(
                        account.getAccountId()
                );

        List<TransactionResponse> responses = transactions.stream()
                .map(this::toResponseWithInstallmentCount)
                .toList();

        log.info(
                "Returning {} transactions for Account Number {} to Agent {}",
                responses.size(),
                normalizedAccountNumber,
                authenticatedAgentId
        );
        return responses;
    }

    // Admin Access
    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByAgentId(Long agentId) {

        List<Transaction> transactions =
                transactionRepository
                        .findByTransactionAccountId_Customer_Agent_AgentId(agentId);

        return transactions.stream()
                .map(this::toResponseWithInstallmentCount)
                .toList();
    }


    //Account Ownership Validation
    private void validateAccountOwnership(Account account, Long authenticatedAgentId) {

        if (!isAccountOwnedByAgent(
                account,
                authenticatedAgentId)) {

            log.warn("Unauthorized transaction access attempt. " +
                            "Agent {} attempted to access Account {} " +
                            "belonging to another Agent",
                    authenticatedAgentId,
                    account.getAccountId()
            );

            throw new AccessDeniedException(
                    "You are not authorized to access transactions " +
                            "for this account"
            );
        }
    }

    //Check Account Ownership
    private boolean isAccountOwnedByAgent(Account account, Long authenticatedAgentId) {
        if (account == null) {
            return false;
        }

        Customer customer = account.getCustomer();

        if (customer == null) {
            return false;
        }

        if (customer.getAgent() == null) {
            return false;
        }

        Long customerAgentId = customer.getAgent().getAgentId();

        return authenticatedAgentId.equals(customerAgentId);
    }

}