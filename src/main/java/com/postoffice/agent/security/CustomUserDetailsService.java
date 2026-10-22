package com.postoffice.agent.security;

import com.postoffice.agent.entity.Admin;
import com.postoffice.agent.entity.Agent;
import com.postoffice.agent.repository.AdminRepository;
import com.postoffice.agent.repository.AgentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AdminRepository adminRepository;
    private final AgentRepository agentRepository;

    public CustomUserDetailsService(AdminRepository adminRepository, AgentRepository agentRepository){
        this.adminRepository = adminRepository;
        this.agentRepository = agentRepository;
    }


    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        log.debug("Attempting to load user: {}", username);

        // Step 1: Search in the Admin table
        Admin admin = adminRepository.findByUsername(username)
                .orElse(null);

        if (admin != null) {
            log.debug("Admin found: {}", username);

            return new CustomUserDetails(
                    admin.getAdminId(),
                    admin.getUsername(),
                    admin.getPassword(),
                    "ADMIN",
                    admin.getAdminStatus().name().equals("ACTIVE"));
        }

        // Step 2: If not an Admin, search in the Agent table
        Agent agent = agentRepository.findByAgentUsername(username)
                .orElse(null);

        if (agent != null) {
            log.debug("Agent found: {}", username);

            return new CustomUserDetails(
                    agent.getAgentId(),
                    agent.getAgentUsername(),
                    agent.getPassword(),
                    "AGENT",
                    agent.getAgentStatus().name().equals("ACTIVE")
            );
        }

        // Step 3: Username does not exist in either table
        log.warn("User not found: {}", username);

        throw new UsernameNotFoundException(
                "User not found with username: " + username);
    }

}