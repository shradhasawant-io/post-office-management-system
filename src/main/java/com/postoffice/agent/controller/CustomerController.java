package com.postoffice.agent.controller;

import com.postoffice.agent.dto.customer.CustomerRequest;
import com.postoffice.agent.dto.customer.CustomerResponse;
import com.postoffice.agent.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    // CREATE CUSTOMER
    @PostMapping
    @PreAuthorize("hasRole('AGENT')")
    public CustomerResponse createCustomer(@RequestBody CustomerRequest request) {
        return customerService.createCustomer(request);
    }

    // GET ALL CUSTOMERS
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<CustomerResponse> getAllCustomers() {
        return customerService.getAllCustomers();
    }

    // GET CUSTOMERS BY AGENT ID
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('AGENT')")
    public CustomerResponse getCustomerById(@PathVariable Long id) {
        return customerService.getCustomerById(id);
    }

    // UPDATE CUSTOMER
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('AGENT')")
    public CustomerResponse updateCustomer(@PathVariable Long id, @RequestBody CustomerRequest request) {
        return customerService.updateCustomer(id, request);
    }

    // SEARCH CUSTOMERS
    @GetMapping("/search")
    @PreAuthorize("hasRole('AGENT')")
    public List<CustomerResponse> searchCustomers(@RequestParam String keyword) {
        return customerService.searchCustomers(keyword);
    }


    // GET AGENT'S CUSTOMERS BY ADMIN
    @GetMapping("/agents/{agentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CustomerResponse>> getCustomersByAgent(@PathVariable Long agentId) {
        System.out.println("getCustomersByAgent " + agentId);
        List<CustomerResponse> customersByAgentId = customerService.getCustomersByAgentId(agentId);
        return ResponseEntity.ok(customersByAgentId);
    }
}