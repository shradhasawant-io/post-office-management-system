package com.postoffice.agent.service.serviceImpl;

import com.postoffice.agent.service.AuthService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    @Override
    public void verifyRole(
            Authentication authentication,
            String selectedRole) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new AccessDeniedException(
                    "Authentication is required."
            );
        }

        String requiredAuthority;

        if ("ADMIN".equalsIgnoreCase(selectedRole)) {

            requiredAuthority = "ROLE_ADMIN";

        } else if ("AGENT".equalsIgnoreCase(selectedRole)) {

            requiredAuthority = "ROLE_AGENT";

        } else {

            throw new IllegalArgumentException(
                    "Invalid role. Allowed roles are ADMIN and AGENT."
            );
        }

        boolean roleMatches =
                authentication.getAuthorities()
                        .stream()
                        .anyMatch(authority ->
                                authority.getAuthority()
                                        .equals(requiredAuthority)
                        );

        if (!roleMatches) {

            throw new AccessDeniedException(
                    "Authenticated credentials do not match the selected role."
            );
        }
    }
}