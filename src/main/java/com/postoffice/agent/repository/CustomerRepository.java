package com.postoffice.agent.repository;

import com.postoffice.agent.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // Search customers across all agents.
    // Use this only for authorized administrative operations.
    @Query("""
        SELECT DISTINCT c
        FROM Customer c
        LEFT JOIN Account a ON a.customer = c
        WHERE
            LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(c.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR c.mobile LIKE CONCAT('%', :keyword, '%')
            OR LOWER(a.accountNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))
        """)
    List<Customer> searchCustomers(
            @Param("keyword") String keyword
    );


    // Search only customers assigned to a specific agent.
    @Query("""
        SELECT DISTINCT c
        FROM Customer c
        LEFT JOIN Account a ON a.customer = c
        WHERE c.agent.agentId = :agentId
          AND (
                LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(c.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR c.mobile LIKE CONCAT('%', :keyword, '%')
                OR LOWER(a.accountNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        """)
    List<Customer> searchCustomersByAgent(
            @Param("keyword") String keyword,
            @Param("agentId") Long agentId
    );


    boolean existsByCustomerCode(String customerCode);

    // Get all customers belonging to a specific agent.
    List<Customer> findAllByAgent_AgentId(Long agentId);


    // Get a specific customer belonging to a specific agent.
    Optional<Customer> findByCustomerIdAndAgent_AgentId(
            Long customerId,
            Long agentId
    );
}