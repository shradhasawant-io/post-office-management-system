package com.postoffice.agent.dto.agent;

import com.postoffice.agent.enums.AgentStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AgentResponse {

    private Long agentId;

    private String agentUsername;

    private String agentName;

    private AgentStatus agentStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
