package com.postoffice.agent.entity;

import com.postoffice.agent.enums.AgentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "agents")
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long agentId;

    @Column(name = "agent_username", nullable = false, unique = true, length = 50)
    private String agentUsername;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String agentName;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentStatus agentStatus = AgentStatus.ACTIVE;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "created_by_admin_id", nullable = false)
    private Admin createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if(agentStatus == null){
            agentStatus = AgentStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
