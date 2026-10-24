package com.postoffice.agent.repository;

import com.postoffice.agent.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {

    boolean existsByAgentUsername(String agentUsername);

    boolean existsByAgentUsernameAndAgentId(String agentUsername, Long agentId);

    Optional<Agent> findByAgentUsername(String agentUsername);

    // Get agents created by a particular Admin
    List<Agent> findByCreatedBy_AdminId(Long adminId);
}