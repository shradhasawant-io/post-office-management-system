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

    //Create Deposit Transaction
    @Override
    @Transactional
    public TransactionResponse createTransaction(TransactionRequest request) {

        if (request == null || request.getTransactionAccountId() == null) {
            throw new BusinessException("Account ID is required");
        }

        if (request.getTransactionAmount() == null || request.getTransactionAmount().signum() <= 0) {
            throw new BusinessException("Transaction amount must be greater than zero");
        }

        Long authenticatedAgentId = authenticatedAgentService.getAuthenticatedAgentId();

        Account account = accountRepository.findById(request.getTransactionAccountId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found with id: " + request.getTransactionAccountId()));

        validateAccountOwnership(account, authenticatedAgentId);

        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException("Transaction cannot be performed on an inactive account");
        }

        // Update account balance
        account.setBalance(account.getBalance().add(request.getTransactionAmount()));
        accountRepository.save(account);

        // Create transaction using your existing entity
        Transaction transaction = TransactionMapper.toEntity(request);

        transaction.setTransactionAccountId(account);
        transaction.setTransactionType(TransactionType.DEPOSIT);
        transaction.setTransactionAmount(request.getTransactionAmount());
        transaction.setBalance(account.getBalance());

        // Set the date if the mapper does not already set it
        if (transaction.getTransactionDate() == null) {
            transaction.setTransactionDate(java.time.LocalDate.now());
        }

        Transaction savedTransaction =
                transactionRepository.save(transaction);

        log.info(
                "Transaction {} created by Agent {} for Account {}. Amount: {}, New Balance: {}",
                savedTransaction.getTransactionId(),
                authenticatedAgentId,
                account.getAccountId(),
                request.getTransactionAmount(),
                account.getBalance()
        );

        return TransactionMapper.toResponse(savedTransaction);
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

        return TransactionMapper.toResponse(transaction);
    }

    // Get all transactions - ADMIN ACCESS
    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getAllTransactions() {

        List<Transaction> transactions = transactionRepository.findAll();

        return transactions.stream()
                .map(TransactionMapper::toResponse)
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

        // 3. Get Transactions
        List<Transaction> transactions = transactionRepository.findAll();

        List<TransactionResponse> responses = new ArrayList<>();

        for (Transaction transaction : transactions) {

            if (transaction.getTransactionAccountId() != null && transaction.getTransactionAccountId().getAccountId().equals(accountId)) {
                responses.add(TransactionMapper.toResponse(transaction));
            }
        }

        log.info("Returning {} transactions for Account {} to Agent {}", responses.size(), accountId, authenticatedAgentId);
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
                .map(TransactionMapper::toResponse)
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