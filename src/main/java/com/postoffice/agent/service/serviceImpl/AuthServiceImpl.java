package com.postoffice.agent.service.serviceImpl;

import com.postoffice.agent.service.AuthService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_AGENT = "ROLE_AGENT";

    @Override
    public String getAuthenticatedRole(Authentication authentication) {

        /*
         * The user must be authenticated before the role
         * can be determined.
         *
         * AnonymousAuthenticationToken is also considered
         * authenticated by Spring Security, so it must be
         * explicitly rejected here.
         */
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {

            throw new BadCredentialsException(
                    "Authentication is required. Please provide valid credentials."
            );
        }

        /*
         * Spring Security has already authenticated the
         * username/password and populated the user's
         * granted authorities.
         *
         * We determine the application role from those
         * authorities instead of accepting a role from
         * the frontend.
         */
        for (GrantedAuthority authority : authentication.getAuthorities()) {

            String authorityName = authority.getAuthority();

            if (ROLE_ADMIN.equals(authorityName)) {
                return "ADMIN";
            }

            if (ROLE_AGENT.equals(authorityName)) {
                return "AGENT";
            }
        }

        /*
         * The credentials were authenticated, but the user
         * does not have one of the application's valid roles.
         */
        throw new AccessDeniedException(
                "Authenticated user does not have a valid application role."
        );
    }
}