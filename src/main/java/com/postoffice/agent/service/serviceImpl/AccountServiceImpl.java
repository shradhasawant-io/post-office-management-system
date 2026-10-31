package com.postoffice.agent.service.serviceImpl;

import com.postoffice.agent.dto.account.AccountRequest;
import com.postoffice.agent.dto.account.AccountResponse;
import com.postoffice.agent.entity.Account;
import com.postoffice.agent.entity.Agent;
import com.postoffice.agent.entity.Customer;
import com.postoffice.agent.entity.Transaction;
import com.postoffice.agent.enums.AccountStatus;
import com.postoffice.agent.enums.CustomerStatus;
import com.postoffice.agent.enums.AccountType;
import com.postoffice.agent.enums.Frequency;
import com.postoffice.agent.enums.PaymentMode;
import com.postoffice.agent.enums.TransactionType;
import com.postoffice.agent.exception.BusinessException;
import com.postoffice.agent.exception.ResourceNotFoundException;
import com.postoffice.agent.mapper.AccountMapper;
import com.postoffice.agent.repository.AccountRepository;
import com.postoffice.agent.repository.CustomerRepository;
import com.postoffice.agent.repository.TransactionRepository;
import com.postoffice.agent.security.AuthenticatedAgentService;
import com.postoffice.agent.service.AccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.time.LocalDate;
import java.math.BigDecimal;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import jakarta.persistence.criteria.Predicate;

@Slf4j
@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final AuthenticatedAgentService authenticatedAgentService;
    private final TransactionRepository transactionRepository;
    private final DailyTransactionNumberGenerator transactionNumberGenerator;

    public AccountServiceImpl(
            AccountRepository accountRepository,
            CustomerRepository customerRepository,
            AuthenticatedAgentService authenticatedAgentService,
            TransactionRepository transactionRepository,
            DailyTransactionNumberGenerator transactionNumberGenerator) {

        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.authenticatedAgentService = authenticatedAgentService;
        this.transactionRepository = transactionRepository;
        this.transactionNumberGenerator = transactionNumberGenerator;
    }

    // Create Account and record the opening deposit as installment #1 atomically.
    @Override
    @Transactional
    public AccountResponse createAccount(AccountRequest request) {

        if (request == null
                || ((request.getCustomerCode() == null || request.getCustomerCode().isBlank())
                && request.getCustomerId() == null)) {
            throw new BusinessException("Customer Code is required");
        }
        if (request.getDepositAmount() == null || request.getDepositAmount().signum() <= 0) {
            throw new BusinessException("Opening deposit amount must be greater than zero");
        }
        if (request.getStartDate() == null) {
            throw new BusinessException("Account start date is required");
        }

        String creationKey = normalizeCreationIdempotencyKey(request.getIdempotencyKey());
        PaymentMode openingPaymentMode = request.getPaymentMode() == null
                ? PaymentMode.CASH : request.getPaymentMode();

        Agent authenticatedAgent = authenticatedAgentService.getAuthenticatedAgent();
        Long agentId = authenticatedAgent.getAgentId();

        // A retry with the same key returns the original account without another installment.
        Account existing = accountRepository.findByCreationIdempotencyKey(creationKey)
                .orElse(null);
        if (existing != null) {
            validateCustomerOwnership(existing.getCustomer(), agentId);
            validateSameAccountCreationRequest(existing, request, openingPaymentMode);
            return toAccountResponseWithInstallmentCount(existing);
        }

        Customer customer = resolveCustomerForAgent(request, agentId);
        validateAccountTypeAndFrequency(request.getAccountType(), request.getFrequency());

        Account account = AccountMapper.toEntity(request);
        account.setCustomer(customer);
        account.setAgent(authenticatedAgent);
        account.setAccountType(request.getAccountType());
        account.setFrequency(request.getFrequency());
        account.setStartDate(request.getStartDate());
        account.setBalance(request.getDepositAmount());
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setCreationIdempotencyKey(creationKey);
        account.setAccountNumber("TMP" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 12));

        Account savedAccount = accountRepository.saveAndFlush(account);
        savedAccount.setAccountNumber(generateAccountNumber(savedAccount.getAccountId()));
        savedAccount = accountRepository.saveAndFlush(savedAccount);

        // The opening deposit is a normal DEPOSIT transaction and therefore installment #1.
        String openingTransactionKey = "ACCOUNT_OPENING:" + creationKey;
        Transaction openingDeposit = Transaction.builder()
                .idempotencyKey(openingTransactionKey)
                .transactionNumber(null)
                .transactionAccountId(savedAccount)
                .transactionDate(request.getStartDate())
                .transactionType(TransactionType.DEPOSIT)
                .transactionAmount(request.getDepositAmount())
                .paymentMode(openingPaymentMode)
                .remarks("Initial account-opening deposit")
                .balance(savedAccount.getBalance())
                .build();

        Transaction savedTransaction = transactionRepository.saveAndFlush(openingDeposit);
        savedTransaction.setTransactionNumber(
                transactionNumberGenerator.generate(savedTransaction.getTransactionDate()));
        transactionRepository.saveAndFlush(savedTransaction);

        log.info(
                "Account {} and opening deposit {} created by Agent {} for Customer {}",
                savedAccount.getAccountNumber(),
                savedTransaction.getTransactionNumber(),
                agentId,
                customer.getCustomerId()
        );

        return toAccountResponseWithInstallmentCount(savedAccount);
    }

    // Resolves the customer only inside the authenticated agent's ownership scope
    // Customer Code is the preferred public identifier. The internal customerId fallback is retained only for older API clients during migration
    private Customer resolveCustomerForAgent(AccountRequest request, Long agentId) {
        Customer customer;

        if (request.getCustomerCode() != null && !request.getCustomerCode().isBlank()) {
            String customerCode = request.getCustomerCode().trim().toUpperCase(Locale.ROOT);
            customer = customerRepository
                    .findByCustomerCodeAndAgentIdForUpdate(customerCode, agentId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Customer not found for the provided Customer Code"));
        } else {
            // Legacy compatibility path; still ownership-scoped.
            customer = customerRepository
                    .findByCustomerIdAndAgentIdForUpdate(request.getCustomerId(), agentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        }

        if (customer.getCustomerStatus() == CustomerStatus.DELETED) {
            throw new ResourceNotFoundException("Customer not found");
        }

        return customer;
    }

    private String normalizeCreationIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException("Idempotency key is required for account creation");
        }
        String normalized = key.trim();
        if (normalized.length() > 80) {
            throw new BusinessException("Idempotency key must not exceed 80 characters");
        }
        return normalized;
    }

    private void validateSameAccountCreationRequest(
            Account existing,
            AccountRequest request,
            PaymentMode requestedPaymentMode) {

        boolean sameCustomer;
        if (request.getCustomerCode() != null && !request.getCustomerCode().isBlank()) {
            sameCustomer = existing.getCustomer().getCustomerCode()
                    .equalsIgnoreCase(request.getCustomerCode().trim());
        } else {
            // Backward compatibility for clients that still send customerId.
            sameCustomer = request.getCustomerId() != null
                    && existing.getCustomer().getCustomerId().equals(request.getCustomerId());
        }

        boolean sameRequest =
                sameCustomer
                        && existing.getAccountType() == request.getAccountType()
                        && existing.getFrequency() == request.getFrequency()
                        && existing.getDepositAmount().compareTo(request.getDepositAmount()) == 0
                        && existing.getStartDate().equals(request.getStartDate());

        if (!sameRequest) {
            throw new BusinessException(
                    "This idempotency key was already used for a different account creation request");
        }

        Transaction openingTransaction = transactionRepository
                .findByIdempotencyKey("ACCOUNT_OPENING:" + existing.getCreationIdempotencyKey())
                .orElseThrow(() -> new BusinessException(
                        "Account opening transaction is missing; contact support"));

        if (openingTransaction.getPaymentMode() != requestedPaymentMode) {
            throw new BusinessException(
                    "This idempotency key was already used for a different account creation request");
        }
    }

    private AccountResponse toAccountResponseWithInstallmentCount(Account account) {
        AccountResponse response = AccountMapper.toResponse(account);
        response.setInstallmentCount(
                transactionRepository.countByTransactionAccountId_AccountIdAndTransactionType(
                        account.getAccountId(), TransactionType.DEPOSIT));
        return response;
    }

    private String generateAccountNumber(Long accountId) {
        String candidate = "ACC" + String.format(Locale.ROOT, "%010d", accountId);

        // Avoid changing or colliding with account numbers already in use.
        if (accountRepository.existsByAccountNumber(candidate)) {
            do {
                candidate = "A" + Long.toString(accountId, 36).toUpperCase(Locale.ROOT)
                        + UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase(Locale.ROOT);
            } while (accountRepository.existsByAccountNumber(candidate));
        }
        return candidate;
    }

    // Get Account By ID
    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long accountId) {

        Long agentId = authenticatedAgentService.getAuthenticatedAgentId();

        if (accountId == null) {
            throw new BusinessException("Account ID is required");
        }

        // Find an account owned by this agent only
        Account account = accountRepository
                .findByAccountIdAndCustomer_Agent_AgentId(accountId, agentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Account not found")
                );

        return toAccountResponseWithInstallmentCount(account);
    }

    // Get All Accounts For Authenticated Agent

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {

        List<Account> accounts = accountRepository.findAll();

        return accounts.stream()
                .map(this::toAccountResponseWithInstallmentCount)
                .toList();
    }


    // Get Accounts By Agent ID
    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByAgentId(Long agentId) {

        List<Account> accounts = accountRepository.findByCustomer_Agent_AgentId(agentId);

        return accounts.stream()
                .map(this::toAccountResponseWithInstallmentCount)
                .toList();
    }

    // Update Account
    @Override
    @Transactional
    public AccountResponse updateAccount(Long accountId, AccountRequest request) {

        Long agentId = authenticatedAgentService.getAuthenticatedAgentId();

        if (accountId == null) {
            throw new BusinessException("Account ID is required");
        }

        if (request == null
                || ((request.getCustomerCode() == null || request.getCustomerCode().isBlank())
                && request.getCustomerId() == null)) {
            throw new BusinessException("Customer Code is required");
        }

        // Find the account only if it belongs to this agent
        Account account = accountRepository
                .findByAccountIdAndCustomer_Agent_AgentId(accountId, agentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Account not found")
                );

        // Find the requested customer
        Customer customer = resolveCustomerForAgent(request, agentId);

        // Do not allow changing the account's customer
        if (!account.getCustomer().getCustomerId()
                .equals(customer.getCustomerId())) {
            throw new BusinessException(
                    "An account cannot be reassigned to another customer"
            );
        }

        validateAccountTypeAndFrequency(
                request.getAccountType(),
                request.getFrequency()
        );

        // Update editable fields only
        account.setAccountType(request.getAccountType());
        account.setDepositAmount(request.getDepositAmount());
        account.setFrequency(request.getFrequency());
        account.setStartDate(request.getStartDate());

        // Do not change account number, agent, or current balance
        Account updatedAccount = accountRepository.save(account);

        log.info(
                "Account {} updated by Agent {}",
                accountId,
                agentId
        );

        return toAccountResponseWithInstallmentCount(updatedAccount);
    }

    // Search Account
    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> searchAccounts(
            String filter,
            String value) {

        Long authenticatedAgentId =
                authenticatedAgentService.getAuthenticatedAgentId();

        if (filter == null || filter.isBlank()) {
            throw new BusinessException(
                    "Filter is required"
            );
        }

        if (value == null || value.isBlank()) {
            throw new BusinessException(
                    "Filter value is required"
            );
        }

        Specification<Account> specification =
                (root, query, criteriaBuilder) -> {

                    // The agent ID comes from the authenticated Spring Security user, NOT from the frontend.
                    Predicate ownershipPredicate =
                            criteriaBuilder.equal(
                                    root.get("agent").get("agentId"),
                                    authenticatedAgentId
                            );

                    Predicate filterPredicate;

                    switch (filter.trim().toUpperCase()) {

                        case "ACCOUNT_NUMBER":

                            filterPredicate =
                                    criteriaBuilder.equal(
                                            root.get("accountNumber"),
                                            value.trim()
                                    );

                            break;


                        case "CUSTOMER_CODE": {
                            Join<Account, Customer> customerJoin =
                                    root.join("customer", JoinType.INNER);

                            filterPredicate = criteriaBuilder.equal(
                                    criteriaBuilder.upper(customerJoin.get("customerCode")),
                                    value.trim().toUpperCase(Locale.ROOT)
                            );
                            break;
                        }

                        case "CUSTOMER_NAME": {
                            Join<Account, Customer> customerJoin =
                                    root.join("customer", JoinType.INNER);

                            String namePattern = "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
                            filterPredicate = criteriaBuilder.like(
                                    criteriaBuilder.lower(customerJoin.get("name")),
                                    namePattern
                            );
                            break;
                        }

                        case "CUSTOMER_MOBILE_NUMBER": {
                            Join<Account, Customer> customerJoin =
                                    root.join("customer", JoinType.INNER);

                            filterPredicate = criteriaBuilder.equal(
                                    customerJoin.get("mobile"),
                                    value.trim()
                            );
                            break;
                        }


                        case "AMOUNT":

                            BigDecimal amount;

                            try {
                                amount =
                                        new BigDecimal(
                                                value.trim()
                                        );
                            } catch (NumberFormatException ex) {

                                throw new BusinessException(
                                        "Amount must be a valid number"
                                );
                            }

                            filterPredicate =
                                    criteriaBuilder.equal(
                                            root.get("depositAmount"),
                                            amount
                                    );

                            break;


                        case "DATE":

                            LocalDate date;

                            try {
                                date =
                                        LocalDate.parse(
                                                value.trim()
                                        );
                            } catch (Exception ex) {

                                throw new BusinessException(
                                        "Date must be in yyyy-MM-dd format"
                                );
                            }

                            filterPredicate =
                                    criteriaBuilder.equal(
                                            root.get("startDate"),
                                            date
                                    );

                            break;


                        case "PAYMENT_MODE":

                            PaymentMode paymentMode;

                            try {
                                paymentMode =
                                        PaymentMode.valueOf(
                                                value.trim().toUpperCase()
                                        );
                            } catch (IllegalArgumentException ex) {

                                throw new BusinessException(
                                        "Invalid payment mode"
                                );
                            }

                            Join<Account, Transaction> transactionJoin =
                                    root.join(
                                            "transactions",
                                            JoinType.INNER
                                    );

                            // One account can have multiple transactions
                            // DISTINCT prevents duplicate Account rows
                            query.distinct(true);

                            filterPredicate =
                                    criteriaBuilder.equal(
                                            transactionJoin.get(
                                                    "paymentMode"
                                            ),
                                            paymentMode
                                    );

                            break;


                        case "ACCOUNT_STATUS":

                            try {
                                filterPredicate = criteriaBuilder.equal(
                                        root.get("accountStatus"),
                                        AccountStatus.valueOf(
                                                value.trim().toUpperCase()
                                        )
                                );
                            } catch (IllegalArgumentException ex) {
                                throw new BusinessException(
                                        "Invalid account status"
                                );
                            }

                            break;


                        case "ACCOUNT_TYPE":

                            try {
                                filterPredicate = criteriaBuilder.equal(
                                        root.get("accountType"),
                                        AccountType.valueOf(
                                                value.trim().toUpperCase()
                                        )
                                );
                            } catch (IllegalArgumentException ex) {
                                throw new BusinessException(
                                        "Invalid account type"
                                );
                            }

                            break;


                        case "BALANCE":

                            BigDecimal balance;

                            try {
                                balance =
                                        new BigDecimal(
                                                value.trim()
                                        );
                            } catch (NumberFormatException ex) {

                                throw new BusinessException(
                                        "Balance must be a valid number"
                                );
                            }

                            filterPredicate =
                                    criteriaBuilder.equal(
                                            root.get("balance"),
                                            balance
                                    );

                            break;


                        default:

                            throw new BusinessException(
                                    "Unsupported account filter: "
                                            + filter
                            );
                    }

                    return criteriaBuilder.and(
                            ownershipPredicate,
                            filterPredicate
                    );
                };

        return accountRepository
                .findAll(specification)
                .stream()
                .map(this::toAccountResponseWithInstallmentCount)
                .toList();
    }



    // Validate Customer Ownership
    private void validateCustomerOwnership(
            Customer customer,
            Long authenticatedAgentId) {

        if (customer.getAgent() == null) {
            throw new AccessDeniedException(
                    "Customer is not assigned to an agent"
            );
        }

        Long customerAgentId = customer.getAgent().getAgentId();

        if (!authenticatedAgentId.equals(customerAgentId)) {
            log.warn(
                    "Agent {} attempted to access Customer {} owned by Agent {}",
                    authenticatedAgentId,
                    customer.getCustomerId(),
                    customerAgentId
            );

            throw new AccessDeniedException(
                    "You are not authorized to access this customer"
            );
        }
    }

    // Validate Account Type and Frequency
    private void validateAccountTypeAndFrequency(
            AccountType accountType,
            Frequency frequency) {

        if (accountType == null || frequency == null) {
            throw new BusinessException(
                    "Account type and frequency are required"
            );
        }

        if (accountType == AccountType.DAILY_SAVING
                && frequency != Frequency.DAILY) {
            throw new BusinessException(
                    "Daily Saving accounts must have DAILY frequency"
            );
        }

        if (accountType == AccountType.MONTHLY_SAVING
                && frequency != Frequency.MONTHLY) {
            throw new BusinessException(
                    "Monthly Saving accounts must have MONTHLY frequency"
            );
        }
    }
}
