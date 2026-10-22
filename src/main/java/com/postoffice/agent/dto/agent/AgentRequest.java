package com.postoffice.agent.dto.agent;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentRequest {

    private String agentUsername;

    private String password;

    private String agentName;
}
