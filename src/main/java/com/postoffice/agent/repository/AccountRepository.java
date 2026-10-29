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

    /**
     * Finds an account by its public account number while enforcing agent ownership
     * in the database query itself.
     */
    Optional<Account> findByAccountNumberAndCustomer_Agent_AgentId(
            String accountNumber,
            Long agentId
    );

    /**
     * Serializes balance updates and idempotency checks for one account.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountId = :accountId")
    Optional<Account> findByIdForUpdate(@Param("accountId") Long accountId);

    List<Account> findByCustomer_Agent_AgentId(Long agentId);
}