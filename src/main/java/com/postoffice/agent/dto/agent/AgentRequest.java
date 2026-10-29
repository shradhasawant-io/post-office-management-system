package com.postoffice.agent.dto.agent;

import jakarta.validation.constraints.Email;
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

    @Email(message = "Enter a valid recovery email address")
    private String email;
}
