package com.postoffice.agent.service;

import com.postoffice.agent.dto.agent.AgentRequest;
import com.postoffice.agent.dto.agent.AgentResponse;

import java.util.List;

public interface AgentService {

    AgentResponse createAgent(AgentRequest request);

    AgentResponse getAgentById(Long agentId);

    List<AgentResponse> getAllAgents();

    List<AgentResponse> getAgentsByAdmin();

    AgentResponse updateAgent(Long agentId, AgentRequest request);

    AgentResponse deactivateAgent(Long agentId);
}