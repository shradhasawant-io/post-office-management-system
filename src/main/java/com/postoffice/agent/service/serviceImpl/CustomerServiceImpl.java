package com.postoffice.agent.service.serviceImpl;

import com.postoffice.agent.dto.customer.CustomerRequest;
import com.postoffice.agent.dto.customer.CustomerResponse;
import com.postoffice.agent.entity.Account;
import com.postoffice.agent.entity.Agent;
import com.postoffice.agent.entity.Customer;
import com.postoffice.agent.enums.AccountStatus;
import com.postoffice.agent.enums.CustomerStatus;
import com.postoffice.agent.enums.TransactionStatus;
import com.postoffice.agent.exception.BusinessException;
import com.postoffice.agent.exception.ResourceNotFoundException;
import com.postoffice.agent.mapper.CustomerMapper;
import com.postoffice.agent.repository.AccountRepository;
import com.postoffice.agent.repository.AgentRepository;
import com.postoffice.agent.repository.CustomerRepository;
import com.postoffice.agent.repository.TransactionRepository;
import com.postoffice.agent.security.AuthenticatedAgentService;
import com.postoffice.agent.service.CustomerService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final AuthenticatedAgentService authenticatedAgentService;
    private final AgentRepository agentRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public CustomerServiceImpl(
            CustomerRepository customerRepository,
            AuthenticatedAgentService authenticatedAgentService,
            AgentRepository agentRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository) {

        this.customerRepository = customerRepository;
        this.authenticatedAgentService = authenticatedAgentService;
        this.agentRepository = agentRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {

        Long authenticatedAgentId =
                authenticatedAgentService.getAuthenticatedAgentId();

        Agent agent = agentRepository.findById(authenticatedAgentId)
                .orElseThrow(() ->
                        new RuntimeException("Authenticated agent not found"));

        Customer customer = CustomerMapper.toEntity(request);
        customer.setAgent(agent);

        // Automatic Customer Code Generation
        customer.setCustomerCode("TMP" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 12));

        Customer savedCustomer = customerRepository.saveAndFlush(customer);

        String customerCode = generateCustomerCode(savedCustomer.getCustomerId());
        savedCustomer.setCustomerCode(customerCode);

        Customer finalCustomer = customerRepository.saveAndFlush(savedCustomer);
        return CustomerMapper.toResponse(finalCustomer);

    }


    private String generateCustomerCode(Long customerId) {
        String candidate = "CID" + String.format(Locale.ROOT, "%08d", customerId);

        // Preserve existing customer codes if an older record already uses the preferred ID-based format.
        if (customerRepository.existsByCustomerCode(candidate)) {
            do {
                candidate = "CID" + Long.toString(customerId, 36).toUpperCase(Locale.ROOT)
                        + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
            } while (customerRepository.existsByCustomerCode(candidate));
        }
        return candidate;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(Long customerId) {

        Long agentId =
                authenticatedAgentService.getAuthenticatedAgentId();

        Customer customer =
                customerRepository
                        .findByCustomerIdAndAgent_AgentId(customerId, agentId)
                        .orElseThrow(() ->
                                new AccessDeniedException("Customer does not belong to the authenticated agent"));

        if (customer.getCustomerStatus() == CustomerStatus.DELETED) {
            throw new ResourceNotFoundException("Customer not found");
        }

        return CustomerMapper.toResponse(customer);
    }

    @Override
    public List<CustomerResponse> getAllCustomers() {

        List<Customer> customers = customerRepository.findAll().stream()
                .filter(customer -> customer.getCustomerStatus() != CustomerStatus.DELETED)
                .toList();

        List<CustomerResponse> responses = new ArrayList<>();

        for (Customer customer : customers) {
            CustomerResponse response = CustomerMapper.toResponse(customer);

            responses.add(response);
        }

        return responses;
    }


    @Override
    public List<CustomerResponse> getCustomersByAgentId(Long agentId) {

        List<Customer> customers = customerRepository.findAllByAgent_AgentId(agentId).stream()
                .filter(customer -> customer.getCustomerStatus() != CustomerStatus.DELETED)
                .toList();

        return customers.stream()
                .map(CustomerMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CustomerResponse updateCustomer(
            Long customerId,
            CustomerRequest request) {

        Long agentId =
                authenticatedAgentService.getAuthenticatedAgentId();

        Customer existingCustomer =
                customerRepository
                        .findByCustomerIdAndAgent_AgentId(
                                customerId,
                                agentId
                        )
                        .orElseThrow(() ->
                                new AccessDeniedException(
                                        "Customer does not belong to the authenticated agent"
                                ));

        if (existingCustomer.getCustomerStatus() == CustomerStatus.DELETED) {
            throw new ResourceNotFoundException("Customer not found");
        }

        existingCustomer.setName(request.getName());
        existingCustomer.setFatherName(request.getFatherName());
        existingCustomer.setEmail(request.getEmail());
        existingCustomer.setMobile(request.getMobile());
        existingCustomer.setDob(request.getDob());
        existingCustomer.setGender(request.getGender());
        existingCustomer.setAddress(request.getAddress());
        existingCustomer.setCity(request.getCity());
        existingCustomer.setState(request.getState());
        existingCustomer.setPinCode(request.getPinCode());
        existingCustomer.setAadharNumber(request.getAadhaar());
        existingCustomer.setPan(request.getPan());

        Customer updatedCustomer =
                customerRepository.save(existingCustomer);

        return CustomerMapper.toResponse(updatedCustomer);
    }


    // SEARCH CUSTOMERS
    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> searchCustomers(String keyword) {

        Long agentId = authenticatedAgentService.getAuthenticatedAgentId();

        List<Customer> customers = customerRepository.searchCustomersByAgent(keyword, agentId)
                .stream()
                .filter(customer -> customer.getCustomerStatus() != CustomerStatus.DELETED)
                .toList();

        return customers.stream()
                .map(CustomerMapper::toResponse)
                .toList();
    }

    /**
     * Soft-deletes a customer while preserving account and transaction history.
     * The customer is scoped to the authenticated agent; the caller cannot
     * supply an agent ID. All linked accounts are made inactive in the same
     * database transaction so no further deposits can be posted to them.
     */
    // Soft-deletes a customer while preserving account and transaction history.
    // The customer is scoped to the authenticated agent; the caller cannot supply an agent ID. All linked accounts are made inactive in the same database transaction so no further deposits can be posted to them
    @Override
    @Transactional
    public void deleteCustomer(Long customerId) {

        if (customerId == null) {
            throw new BusinessException("Customer ID is required");
        }

        Long agentId = authenticatedAgentService.getAuthenticatedAgentId();

        // Lock the customer first. Account creation uses the same lock, so a new account cannot be created while this customer is being deactivated.
        Customer customer = customerRepository
                .findByCustomerIdAndAgentIdForUpdate(customerId, agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        if (customer.getCustomerStatus() == CustomerStatus.DELETED) {
            throw new ResourceNotFoundException("Customer not found");
        }

        // Lock account rows in a stable order. Deposits lock the same account
        // rows before changing balances, so deletion waits for in-flight deposits.
        List<Account> linkedAccounts =
                accountRepository.findByCustomer_CustomerIdForUpdate(customerId);

        for (Account account : linkedAccounts) {
            account.setAccountStatus(AccountStatus.INACTIVE);
        }
        accountRepository.saveAll(linkedAccounts);

        // Mark every transaction belonging to those accounts inactive, but
        // retain transaction rows, amounts, balances and transaction numbers.
        if (!linkedAccounts.isEmpty()) {
            List<Long> accountIds = linkedAccounts.stream()
                    .map(Account::getAccountId)
                    .toList();

            List<com.postoffice.agent.entity.Transaction> linkedTransactions =
                    transactionRepository.findByTransactionAccountId_AccountIdIn(accountIds);

            for (com.postoffice.agent.entity.Transaction transaction : linkedTransactions) {
                transaction.setTransactionStatus(TransactionStatus.INACTIVE);
            }

            transactionRepository.saveAll(linkedTransactions);
        }

        customer.setCustomerStatus(CustomerStatus.DELETED);
        customerRepository.save(customer);
    }
}