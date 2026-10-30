package com.postoffice.agent.service.serviceImpl;

import com.postoffice.agent.dto.customer.CustomerRequest;
import com.postoffice.agent.dto.customer.CustomerResponse;
import com.postoffice.agent.entity.Agent;
import com.postoffice.agent.entity.Customer;
import com.postoffice.agent.mapper.CustomerMapper;
import com.postoffice.agent.repository.AgentRepository;
import com.postoffice.agent.repository.CustomerRepository;
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

    public CustomerServiceImpl(
            CustomerRepository customerRepository,
            AuthenticatedAgentService authenticatedAgentService,
            AgentRepository agentRepository) {

        this.customerRepository = customerRepository;
        this.authenticatedAgentService = authenticatedAgentService;
        this.agentRepository = agentRepository;
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

        /*
         * customer_code is NOT NULL and UNIQUE in the existing schema.
         * Save once with a temporary unique value to obtain the database
         * generated customerId, then replace it with the business code.
         * Both writes occur in this transaction, so the temporary value
         * is never committed as the final customer code.
         */
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

        // Preserve existing customer codes if an older record already uses
        // the preferred ID-based format.
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

        return CustomerMapper.toResponse(customer);
    }

    @Override
    public List<CustomerResponse> getAllCustomers() {

        List<Customer> customers = customerRepository.findAll();

        List<CustomerResponse> responses = new ArrayList<>();

        for (Customer customer : customers) {
            CustomerResponse response = CustomerMapper.toResponse(customer);

            responses.add(response);
        }

        return responses;
    }


    @Override
    public List<CustomerResponse> getCustomersByAgentId(Long agentId) {

        List<Customer> customers = customerRepository.findAllByAgent_AgentId(agentId);

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

        List<Customer> customers = customerRepository.searchCustomersByAgent(keyword, agentId);

        return customers.stream()
                .map(CustomerMapper::toResponse)
                .toList();
    }
}