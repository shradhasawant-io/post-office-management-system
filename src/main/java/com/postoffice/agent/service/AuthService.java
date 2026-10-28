package com.postoffice.agent.service;

import org.springframework.security.core.Authentication;

public interface AuthService {

    String getAuthenticatedRole(Authentication authentication);
}