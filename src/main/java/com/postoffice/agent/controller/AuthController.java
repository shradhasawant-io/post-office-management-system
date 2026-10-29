package com.postoffice.agent.controller;

import com.postoffice.agent.service.AuthService;
import com.postoffice.agent.service.PasswordResetService;
import com.postoffice.agent.dto.auth.ForgotPasswordRequest;
import com.postoffice.agent.dto.auth.ResetPasswordRequest;
import jakarta.validation.Valid;
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
    private final PasswordResetService passwordResetService;

    @PostMapping("/verify-role")
    public ResponseEntity<Map<String, String>> verifyRole(
            Authentication authentication) {

        String role = authService.getAuthenticatedRole(authentication);

        return ResponseEntity.ok(
                Map.of("role", role)
        );
    }
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request);
        return ResponseEntity.ok(Map.of(
                "message", "If the supplied username and recovery email match an active account, a password reset link will be sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(Map.of(
                "message", "Your password has been reset. Please sign in with your new password."));
    }

}