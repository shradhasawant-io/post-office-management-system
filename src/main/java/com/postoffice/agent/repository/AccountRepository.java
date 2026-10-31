package com.postoffice.agent.repository;

import com.postoffice.agent.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {

    Optional<Account> findByAccountIdAndCustomer_Agent_AgentId(Long accountId, Long agentId);

    boolean existsByAccountNumber(String accountNumber);

    Optional<Account> findByCreationIdempotencyKey(String creationIdempotencyKey);

    // Finds an account by its public account number while enforcing agent ownership in the database query itself
    Optional<Account> findByAccountNumberAndCustomer_Agent_AgentId(
            String accountNumber,
            Long agentId
    );

    // Finds and locks an account by its public account number within the authenticated agent's ownership scope before changing its balance
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a
            FROM Account a
            JOIN a.customer c
            WHERE a.accountNumber = :accountNumber
              AND c.agent.agentId = :agentId
            """)
    Optional<Account> findByAccountNumberAndAgentIdForUpdate(
            @Param("accountNumber") String accountNumber,
            @Param("agentId") Long agentId
    );


    // Serializes balance updates and idempotency checks for one account
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountId = :accountId")
    Optional<Account> findByIdForUpdate(@Param("accountId") Long accountId);

    List<Account> findByCustomer_Agent_AgentId(Long agentId);

    List<Account> findByCustomer_CustomerId(Long customerId);

    // Locks every account belonging to a customer before deactivation
    // This serializes customer deactivation with deposits that lock account rows
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a
            FROM Account a
            WHERE a.customer.customerId = :customerId
            ORDER BY a.accountId
            """)
    List<Account> findByCustomer_CustomerIdForUpdate(
            @Param("customerId") Long customerId
    );
}