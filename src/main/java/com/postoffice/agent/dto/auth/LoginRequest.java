package com.postoffice.agent.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class LoginRequest {

    @NotBlank(message = "Selected role is required.")
    @Pattern(
            regexp = "ADMIN|AGENT",
            flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "Selected role must be ADMIN or AGENT."
    )
    private String selectedRole;

    public String getSelectedRole() {
        return selectedRole;
    }

    public void setSelectedRole(String selectedRole) {
        this.selectedRole = selectedRole;
    }
}