package com.postoffice.agent.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticatedAdminService {

    // Returns the ID of the currently authenticated admin
    public Long getAuthenticatedAdminId() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            throw new IllegalStateException("No authentication information found");
        }

        if (!authentication.isAuthenticated()) {
            throw new IllegalStateException("Admin is not authenticated");
        }

        if (authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("Anonymous user cannot perform this operation");
        }

        Object principal = authentication.getPrincipal();

        if (!(principal instanceof CustomUserDetails userDetails)) {
            throw new IllegalStateException("Authenticated principal is not CustomUserDetails");
        }

        if (!"ADMIN".equalsIgnoreCase(userDetails.getRole())) {
            throw new IllegalStateException("Authenticated user is not an admin");
        }

        return userDetails.getUserId();
    }
}