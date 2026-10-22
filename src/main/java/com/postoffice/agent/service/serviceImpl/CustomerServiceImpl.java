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

        Customer savedCustomer = customerRepository.save(customer);

        return CustomerMapper.toResponse(savedCustomer);

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

        List<Customer> customers = customerRepository.findByAgent_AgentId(agentId);

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

        List<Customer> customers = customerRepository.searchCustomersByAgent(keyword, -1L, agentId);

        return customers.stream()
                .map(CustomerMapper::toResponse)
                .toList();
    }
}