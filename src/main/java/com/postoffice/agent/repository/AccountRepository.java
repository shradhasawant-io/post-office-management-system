package com.postoffice.agent.repository;

import com.postoffice.agent.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountIdAndCustomer_Agent_AgentId(Long accountId, Long agentId);

    List<Account> findByCustomer_Agent_AgentId(Long agentId);
}