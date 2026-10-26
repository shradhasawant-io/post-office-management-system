package com.postoffice.agent.controller;

import com.postoffice.agent.dto.auth.LoginRequest;
import com.postoffice.agent.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/verify-role")
    public ResponseEntity<Void> verifyRole(
            @Valid @RequestBody LoginRequest request,
            Authentication authentication) {

        authService.verifyRole(
                authentication,
                request.getSelectedRole()
        );

        return ResponseEntity.ok().build();
    }
}