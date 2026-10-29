package com.postoffice.agent.dto.admin;

import com.postoffice.agent.enums.AdminStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminResponse {

    private Long adminId;

    private String username;

    private String adminName;

    private String email;

    private AdminStatus adminStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}