package com.postoffice.agent.service;

import com.postoffice.agent.dto.customer.CustomerRequest;
import com.postoffice.agent.dto.customer.CustomerResponse;

import java.util.List;

public interface CustomerService {

    CustomerResponse createCustomer(CustomerRequest request);

    CustomerResponse getCustomerById(Long customerId);

    List<CustomerResponse> getAllCustomers();

    CustomerResponse updateCustomer(Long customerId, CustomerRequest request);

    void deleteCustomer(Long customerId);

    List<CustomerResponse> searchCustomers(String keyword);

    List<CustomerResponse> getCustomersByAgentId(Long agentId);

}