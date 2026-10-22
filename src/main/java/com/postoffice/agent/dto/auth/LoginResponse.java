package com.postoffice.agent.dto.auth;

import com.postoffice.agent.enums.UserRole;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private String accessToken;

    private String tokenType;

    private long expiresIn;

    private String username;

    private UserRole role;
}