package com.postoffice.agent.controller;

import com.postoffice.agent.dto.agent.AgentRequest;
import com.postoffice.agent.dto.agent.AgentResponse;
import com.postoffice.agent.service.AgentService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    // CREATE AGENT
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgentResponse> createAgent(@Valid @RequestBody AgentRequest request) {

        log.info("create Agent request");

        AgentResponse response = agentService.createAgent(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // GET AGENT BY ID
    @GetMapping("/{agentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgentResponse> getAgentById(@PathVariable Long agentId) {

        AgentResponse response = agentService.getAgentById(agentId);
        return ResponseEntity.ok(response);
    }

    // GET ALL AGENTS
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AgentResponse>> getAllAgents() {
        return ResponseEntity.ok(agentService.getAllAgents());
    }

    // UPDATE AGENT
    @PutMapping("/{agentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgentResponse> updateAgent(@PathVariable Long agentId,
            @Valid @RequestBody AgentRequest request) {

        AgentResponse response = agentService.updateAgent(agentId, request);
        return ResponseEntity.ok(response);
    }

    // DEACTIVATE AGENT
    @PatchMapping("/{agentId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgentResponse> deactivateAgent(
            @PathVariable Long agentId) {

        AgentResponse response = agentService.deactivateAgent(agentId);

        return ResponseEntity.ok(response);
    }
}