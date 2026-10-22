package com.postoffice.agent.service.serviceImpl;

import com.postoffice.agent.dto.agent.AgentRequest;
import com.postoffice.agent.dto.agent.AgentResponse;
import com.postoffice.agent.entity.Admin;
import com.postoffice.agent.entity.Agent;
import com.postoffice.agent.enums.AgentStatus;
import com.postoffice.agent.exception.BusinessException;
import com.postoffice.agent.exception.ResourceNotFoundException;
import com.postoffice.agent.mapper.AgentMapper;
import com.postoffice.agent.repository.AdminRepository;
import com.postoffice.agent.repository.AgentRepository;
import com.postoffice.agent.security.AuthenticatedAdminService;
import com.postoffice.agent.service.AgentService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class AgentServiceImpl implements AgentService {

    private final AgentRepository agentRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticatedAdminService authenticatedAdminService;

    public AgentServiceImpl(
            AgentRepository agentRepository,
            AdminRepository adminRepository,
            PasswordEncoder passwordEncoder,
            AuthenticatedAdminService authenticatedAdminService) {

        this.agentRepository = agentRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticatedAdminService = authenticatedAdminService;
    }


    // CREATE AGENT
    @Override
    @Transactional
    public AgentResponse createAgent(AgentRequest request) {

        Long authenticatedAdminId = authenticatedAdminService.getAuthenticatedAdminId();

        log.info("Admin {} attempting to create Agent with username {}", authenticatedAdminId,
                request.getAgentUsername()
        );

        // Find the currently authenticated Admin.
        Admin admin = adminRepository.findById(authenticatedAdminId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Authenticated admin not found"));

        // Duplicate username check
        if (agentRepository.existsByAgentUsername(request.getAgentUsername())) {

            log.warn("Agent creation failed. Username already exists: {}", request.getAgentUsername());

            throw new BusinessException("Agent username already exists");
        }

        // Convert request to entity
        Agent agent = AgentMapper.toEntity(request);

        // BCrypt password encoding
        agent.setPassword(passwordEncoder.encode(request.getPassword()));


         //Store which Admin created this Agent.
         // Admin A creates Agent X
         // Agent X.createdBy = Admin A
        agent.setCreatedBy(admin);

        // Save Agent
        Agent savedAgent = agentRepository.save(agent);

        log.info("Agent {} created successfully by Admin {}", savedAgent.getAgentId(), authenticatedAdminId);

        return AgentMapper.toResponse(savedAgent);
    }


    // GET AGENT BY ID
    @Override
    @Transactional(readOnly = true)
    public AgentResponse getAgentById(Long agentId) {

        Long authenticatedAdminId = authenticatedAdminService.getAuthenticatedAdminId();

        log.info("Admin {} attempting to access Agent {}", authenticatedAdminId, agentId);

        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> {

                    log.warn("Agent {} not found", agentId);

                    return new ResourceNotFoundException("Agent not found with id: " + agentId);
                });

        // Only the Admin who created this Agent can access this Agent
        validateAdminOwnership(agent, authenticatedAdminId);

        return AgentMapper.toResponse(agent);
    }


    // GET ALL AGENTS
    @Override
    @Transactional(readOnly = true)
    public List<AgentResponse> getAllAgents() {

        Long authenticatedAdminId = authenticatedAdminService.getAuthenticatedAdminId();

        log.info("Admin {} requesting all agents", authenticatedAdminId);

        // Fetch all agents, regardless of the creating admin
        List<Agent> agents = agentRepository.findAll();

        return agents.stream()
                .map(AgentMapper::toResponse)
                .toList();
    }

    // GET MY AGENTS
    @Override
    public List<AgentResponse> getAgentsByAdmin() {
        Long authenticatedAdminId = authenticatedAdminService.getAuthenticatedAdminId();

        log.info("Admin {} requesting agents created by them", authenticatedAdminId);

        List<Agent> agents = agentRepository.findByCreatedBy_AdminId(authenticatedAdminId);

        return agents.stream()
                .map(AgentMapper::toResponse)
                .toList();
    }


    // UPDATE AGENT
    @Override
    @Transactional
    public AgentResponse updateAgent(Long agentId, AgentRequest request) {

        Long authenticatedAdminId = authenticatedAdminService.getAuthenticatedAdminId();

        log.info("Admin {} attempting to update Agent {}", authenticatedAdminId, agentId);

        // 1. Find Agent
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Agent not found with id: " + agentId));

        // 2. Check Admin ownership
        validateAdminOwnership(agent, authenticatedAdminId);

        // 3. Check Agent status before updating
        if (agent.getAgentStatus() != AgentStatus.ACTIVE) {
            throw new BusinessException("Cannot update an inactive agent");
        }

        // 4. Check duplicate username
        if (agentRepository.existsByAgentUsernameAndAgentIdNot(request.getAgentUsername(), agentId)) {

            throw new BusinessException("Agent username already exists");
        }

        // 5. Update username
        agent.setAgentUsername(request.getAgentUsername());

        // 6. Update name
        agent.setAgentName(request.getAgentName());

        // 7. Update password only if a new password is provided
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            agent.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        // 8. Update timestamp
        agent.setUpdatedAt(LocalDateTime.now());

        // 9. Save updated Agent
        Agent updatedAgent = agentRepository.save(agent);

        log.info("Agent {} successfully updated by Admin {}", agentId, authenticatedAdminId);

        return AgentMapper.toResponse(updatedAgent);
    }



    // DEACTIVATE AGENT
    @Override
    @Transactional
    public AgentResponse deactivateAgent(Long agentId) {

        Long authenticatedAdminId = authenticatedAdminService.getAuthenticatedAdminId();

        log.info("Admin {} attempting to deactivate Agent {}", authenticatedAdminId, agentId);

        // Find Agent
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> {

                    log.warn("Agent {} not found", agentId);

                    return new ResourceNotFoundException("Agent not found with id: " + agentId);
                });

        // Ownership check
        validateAdminOwnership(agent, authenticatedAdminId);

        // Change status
        agent.setAgentStatus(AgentStatus.INACTIVE);

        // Update timestamp
        agent.setUpdatedAt(LocalDateTime.now());

        Agent updatedAgent = agentRepository.save(agent);

        log.info("Agent {} deactivated successfully by Admin {}", agentId, authenticatedAdminId);

        return AgentMapper.toResponse(updatedAgent);
    }


    // ADMIN OWNERSHIP VALIDATION
    private void validateAdminOwnership(
            Agent agent,
            Long authenticatedAdminId) {

        // Agent must have a creator Admin
        if (agent.getCreatedBy() == null) {

            log.error("Agent {} has no creator Admin", agent.getAgentId());

            throw new AccessDeniedException("Agent has no assigned creator admin");
        }

        // Get creator Admin ID
        Long creatorAdminId = agent.getCreatedBy().getAdminId();

        // Compare authenticated Admin with Agent creator Admin
        if (!authenticatedAdminId.equals(creatorAdminId)) {

            log.warn("Unauthorized Agent access attempt. " +
                            "Admin {} attempted to access Agent {} " +
                            "created by Admin {}",
                    authenticatedAdminId,
                    agent.getAgentId(),
                    creatorAdminId
            );

            throw new AccessDeniedException("You are not authorized to access this agent");
        }
    }
}