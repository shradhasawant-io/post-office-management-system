package com.postoffice.agent.security;

import com.postoffice.agent.entity.Agent;
import com.postoffice.agent.repository.AgentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticatedAgentService {

    private final AgentRepository agentRepository;

    public AuthenticatedAgentService(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public Agent getAuthenticatedAgent() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated");
        }

        boolean isAgent = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_AGENT"));

        if (!isAgent) {
            throw new AccessDeniedException("Only an agent can perform this operation");
        }

        String username = authentication.getName();

        return agentRepository
                .findByAgentUsername(username)
                .orElseThrow(() ->
                        new AccessDeniedException("Authenticated agent not found"));
    }

    public Long getAuthenticatedAgentId() {
        return getAuthenticatedAgent().getAgentId();
    }
}