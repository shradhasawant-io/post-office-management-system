package com.postoffice.agent.mapper;

import com.postoffice.agent.dto.agent.AgentRequest;
import com.postoffice.agent.dto.agent.AgentResponse;
import com.postoffice.agent.entity.Agent;

public class AgentMapper {

    public static Agent toEntity(AgentRequest request) {

        return Agent.builder()
                .agentUsername(request.getAgentUsername())
                .password(request.getPassword())
                .agentName(request.getAgentName())
                // Add your other AgentRequest fields here
                .build();
    }

    public static AgentResponse toResponse(Agent agent) {

        return AgentResponse.builder()
                .agentId(agent.getAgentId())
                .agentName(agent.getAgentName())
                .agentUsername(agent.getAgentUsername())
                .agentStatus(agent.getAgentStatus())
                .createdAt(agent.getCreatedAt())
                .updatedAt(agent.getUpdatedAt())
                .build();
    }
}