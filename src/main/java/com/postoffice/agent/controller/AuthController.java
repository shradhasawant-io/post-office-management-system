package com.postoffice.agent.controller;

import com.postoffice.agent.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/verify-role")
    public ResponseEntity<Map<String, String>> verifyRole(
            Authentication authentication) {

        String role = authService.getAuthenticatedRole(authentication);

        return ResponseEntity.ok(
                Map.of("role", role)
        );
    }
}