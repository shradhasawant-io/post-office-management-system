package com.postoffice.agent.service.serviceImpl;

import com.postoffice.agent.dto.account.AccountRequest;
import com.postoffice.agent.dto.account.AccountResponse;
import com.postoffice.agent.entity.Account;
import com.postoffice.agent.entity.Agent;
import com.postoffice.agent.entity.Customer;
import com.postoffice.agent.enums.AccountStatus;
import com.postoffice.agent.enums.AccountType;
import com.postoffice.agent.enums.Frequency;
import com.postoffice.agent.exception.BusinessException;
import com.postoffice.agent.exception.ResourceNotFoundException;
import com.postoffice.agent.mapper.AccountMapper;
import com.postoffice.agent.repository.AccountRepository;
import com.postoffice.agent.repository.CustomerRepository;
import com.postoffice.agent.security.AuthenticatedAgentService;
import com.postoffice.agent.service.AccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final AuthenticatedAgentService authenticatedAgentService;

    public AccountServiceImpl(
            AccountRepository accountRepository,
            CustomerRepository customerRepository,
            AuthenticatedAgentService authenticatedAgentService) {

        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.authenticatedAgentService = authenticatedAgentService;
    }

    // Create Account
    @Override
    @Transactional
    public AccountResponse createAccount(AccountRequest request) {

        if (request == null || request.getCustomerId() == null) {
            throw new BusinessException("Customer ID is required");
        }

        // Get the currently authenticated agent
        Agent authenticatedAgent =
                authenticatedAgentService.getAuthenticatedAgent();

        Long agentId = authenticatedAgent.getAgentId();

        log.info(
                "Agent {} attempting to create account for customer {}",
                agentId,
                request.getCustomerId()
        );

        // Find the requested customer
        Customer customer = customerRepository
                .findById(request.getCustomerId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Customer not found")
                );

        // Ensure this customer belongs to the authenticated agent
        validateCustomerOwnership(customer, agentId);

        // Validate account type and frequency
        validateAccountTypeAndFrequency(
                request.getAccountType(),
                request.getFrequency()
        );

        // Convert request DTO into Account entity
        Account account = AccountMapper.toEntity(request);

        account.setCustomer(customer);

        // NEW: Associate this account with the authenticated agent
        account.setAgent(authenticatedAgent);

        account.setAccountNumber(request.getAccountNumber());
        account.setAccountType(request.getAccountType());
        account.setFrequency(request.getFrequency());
        account.setStartDate(request.getStartDate());
        account.setBalance(request.getDepositAmount());
        account.setAccountStatus(AccountStatus.ACTIVE);

        // Save account, including agent_id
        Account savedAccount = accountRepository.save(account);

        log.info(
                "Account {} created by Agent {} for Customer {}",
                savedAccount.getAccountId(),
                agentId,
                customer.getCustomerId()
        );

        return AccountMapper.toResponse(savedAccount);
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

        return AccountMapper.toResponse(account);
    }

    // Get All Accounts For Authenticated Agent

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {

        List<Account> accounts = accountRepository.findAll();

        return accounts.stream()
                .map(AccountMapper::toResponse)
                .toList();
    }


    // Get Accounts By Agent ID
    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByAgentId(Long agentId) {

        List<Account> accounts = accountRepository.findByCustomer_Agent_AgentId(agentId);

        return accounts.stream()
                .map(AccountMapper::toResponse)
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

        if (request == null || request.getCustomerId() == null) {
            throw new BusinessException("Customer ID is required");
        }

        // Find the account only if it belongs to this agent
        Account account = accountRepository
                .findByAccountIdAndCustomer_Agent_AgentId(accountId, agentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Account not found")
                );

        // Find the requested customer
        Customer customer = customerRepository
                .findById(request.getCustomerId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Customer not found")
                );

        // Check that the customer belongs to this agent
        validateCustomerOwnership(customer, agentId);

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

        return AccountMapper.toResponse(updatedAccount);
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
