package com.postoffice.agent.service;

import org.springframework.security.core.Authentication;

public interface AuthService {

    void verifyRole(
            Authentication authentication,
            String selectedRole
    );
}