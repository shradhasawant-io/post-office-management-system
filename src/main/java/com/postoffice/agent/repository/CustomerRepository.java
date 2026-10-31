package com.postoffice.agent.repository;

import com.postoffice.agent.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
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

    /**
     * Resolve a customer by its public code within the authenticated agent's
     * ownership scope. Never use an unscoped customer-code lookup for agent actions.
     */
    Optional<Customer> findByCustomerCodeAndAgent_AgentId(
            String customerCode,
            Long agentId
    );

    /**
     * Locks the customer row while account creation/deletion is in progress.
     * This serializes account creation against customer archival.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT c
        FROM Customer c
        WHERE c.customerCode = :customerCode
          AND c.agent.agentId = :agentId
        """)
    Optional<Customer> findByCustomerCodeAndAgentIdForUpdate(
            @Param("customerCode") String customerCode,
            @Param("agentId") Long agentId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT c
        FROM Customer c
        WHERE c.customerId = :customerId
          AND c.agent.agentId = :agentId
        """)
    Optional<Customer> findByCustomerIdAndAgentIdForUpdate(
            @Param("customerId") Long customerId,
            @Param("agentId") Long agentId
    );

    // Get all customers belonging to a specific agent.
    List<Customer> findAllByAgent_AgentId(Long agentId);


    // Get a specific customer belonging to a specific agent.
    Optional<Customer> findByCustomerIdAndAgent_AgentId(
            Long customerId,
            Long agentId
    );
}