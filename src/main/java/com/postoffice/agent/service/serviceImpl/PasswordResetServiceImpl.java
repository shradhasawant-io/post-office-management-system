package com.postoffice.agent.service.serviceImpl;

import com.postoffice.agent.dto.auth.ForgotPasswordRequest;
import com.postoffice.agent.dto.auth.ResetPasswordRequest;
import com.postoffice.agent.entity.Admin;
import com.postoffice.agent.entity.Agent;
import com.postoffice.agent.entity.PasswordResetToken;
import com.postoffice.agent.enums.AdminStatus;
import com.postoffice.agent.enums.AgentStatus;
import com.postoffice.agent.exception.BusinessException;
import com.postoffice.agent.repository.AdminRepository;
import com.postoffice.agent.repository.AgentRepository;
import com.postoffice.agent.repository.PasswordResetTokenRepository;
import com.postoffice.agent.service.PasswordResetService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final long TOKEN_LIFETIME_MINUTES = 15;
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_AGENT = "AGENT";

    private final AdminRepository adminRepository;
    private final AgentRepository agentRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    public PasswordResetServiceImpl(
            AdminRepository adminRepository,
            AgentRepository agentRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender) {
        this.adminRepository = adminRepository;
        this.agentRepository = agentRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
    }

    @Override
    @Async
    @Transactional
    public void requestReset(ForgotPasswordRequest request) {
        String username = request.getUsername().trim();
        String suppliedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);

        // Username collision resolution follows the same Admin-first rule as CustomUserDetailsService.
        Admin admin = adminRepository.findByUsername(username).orElse(null);
        Agent agent = null;
        String role;
        String registeredEmail;
        boolean active;

        if (admin != null) {
            role = ROLE_ADMIN;
            registeredEmail = normalizeEmail(admin.getEmail());
            active = admin.getAdminStatus() == AdminStatus.ACTIVE;
        } else {
            agent = agentRepository.findByAgentUsername(username).orElse(null);
            if (agent == null) return;
            role = ROLE_AGENT;
            registeredEmail = normalizeEmail(agent.getEmail());
            active = agent.getAgentStatus() == AgentStatus.ACTIVE;
        }

        if (!active || registeredEmail == null || !MessageDigest.isEqual(
                registeredEmail.getBytes(StandardCharsets.UTF_8),
                suppliedEmail.getBytes(StandardCharsets.UTF_8))) {
            return;
        }

        // Invalidate all prior unused reset tokens for this user before issuing a replacement.
        LocalDateTime now = LocalDateTime.now();
        List<PasswordResetToken> oldTokens = tokenRepository
                .findByUsernameAndUserRoleAndUsedAtIsNull(username, role);
        oldTokens.forEach(token -> token.setUsedAt(now));
        tokenRepository.saveAll(oldTokens);

        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        PasswordResetToken token = new PasswordResetToken();
        token.setUsername(username);
        token.setUserRole(role);
        token.setTokenHash(sha256(rawToken));
        token.setExpiresAt(now.plusMinutes(TOKEN_LIFETIME_MINUTES));
        tokenRepository.save(token);

        String baseUrl = appBaseUrl.replaceAll("/+$", "");
        String resetUrl = baseUrl + "/reset-password.html?token=" + rawToken;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setTo(registeredEmail);
            if (mailFrom != null && !mailFrom.isBlank()) helper.setFrom(mailFrom);
            helper.setSubject("Post Office Agent System - Password reset");
            helper.setText(
                    "<p>A password reset was requested for your Post Office Agent System account.</p>" +
                            "<p><a href=\"" + resetUrl + "\">Reset your password</a></p>" +
                            "<p>This link expires in 15 minutes and can be used once. If you did not request this, you can ignore this email.</p>",
                    true);
            mailSender.send(message);

        } catch (MailException | MessagingException ex) {
            // The email was not sent successfully.
            // Invalidate this token so an unsent reset link cannot be used.
            token.setUsedAt(LocalDateTime.now());
            tokenRepository.save(token);

            // Log the full exception on the server for troubleshooting.
            // Never log the raw token, reset URL, recipient, or SMTP credentials.
            log.error(
                    "Password reset email delivery failed. role={}, exceptionType={}",
                    role,
                    ex.getClass().getSimpleName(),
                    ex
            );
        }

    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("New password and confirmation do not match.");
        }
        int passwordBytes = request.getNewPassword().getBytes(StandardCharsets.UTF_8).length;
        if (request.getNewPassword().length() < 8 || passwordBytes > 72) {
            throw new BusinessException("Password must contain at least 8 characters and no more than 72 UTF-8 bytes.");
        }

        String tokenHash = sha256(request.getToken().trim());
        PasswordResetToken token = tokenRepository.findUnusedByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new BusinessException("The password reset link is invalid or has expired."));

        LocalDateTime now = LocalDateTime.now();
        if (!token.getExpiresAt().isAfter(now)) {
            throw new BusinessException("The password reset link is invalid or has expired.");
        }

        if (ROLE_ADMIN.equals(token.getUserRole())) {
            Admin admin = adminRepository.findByUsername(token.getUsername())
                    .orElseThrow(() -> new BusinessException("The password reset link is invalid or has expired."));
            if (admin.getAdminStatus() != AdminStatus.ACTIVE) {
                throw new BusinessException("The password reset link is invalid or has expired.");
            }
            admin.setPassword(passwordEncoder.encode(request.getNewPassword()));
            adminRepository.save(admin);
        } else if (ROLE_AGENT.equals(token.getUserRole())) {
            Agent agent = agentRepository.findByAgentUsername(token.getUsername())
                    .orElseThrow(() -> new BusinessException("The password reset link is invalid or has expired."));
            if (agent.getAgentStatus() != AgentStatus.ACTIVE) {
                throw new BusinessException("The password reset link is invalid or has expired.");
            }
            agent.setPassword(passwordEncoder.encode(request.getNewPassword()));
            agentRepository.save(agent);
        } else {
            throw new BusinessException("The password reset link is invalid or has expired.");
        }

        token.setUsedAt(now);
        tokenRepository.save(token);

        // Invalidate any other outstanding links for this account after successful reset.
        List<PasswordResetToken> otherTokens = tokenRepository
                .findByUsernameAndUserRoleAndUsedAtIsNull(token.getUsername(), token.getUserRole());
        otherTokens.forEach(other -> other.setUsedAt(now));
        tokenRepository.saveAll(otherTokens);
    }

    private static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
