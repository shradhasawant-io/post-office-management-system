package com.postoffice.agent.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(CustomUserDetailsService customUserDetailsService,
                          PasswordEncoder passwordEncoder,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.customUserDetailsService = customUserDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }


    // Authentication provider
    // UserDetailsService + PasswordEncoder -> DaoAuthenticationProvider
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    //AuthenticationManager
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }


    // HTTP Security Configuration
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http

                // ------------------------------------------------
                // CSRF
                // ------------------------------------------------
                .csrf(csrf -> csrf.disable())

                // ------------------------------------------------
                // Authorization
                // ------------------------------------------------
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // ====================================================
                        // PUBLIC FRONTEND RESOURCES
                        // ====================================================
                        //
                        // Login page must be publicly accessible because
                        // the user is not authenticated yet.

                        // Static frontend assets must load before the user has credentials
                        .requestMatchers(HttpMethod.GET,
                                "/",
                                "/index.html",
                                "/css/**",
                                "/js/**",
                                "/favicon.ico")
                        .permitAll()

                        // ====================================================
                        // ROLE VERIFICATION
                        // ====================================================
                        //
                        // The endpoint receives HTTP Basic credentials from
                        // the frontend and AuthService/AuthServiceImpl checks
                        // whether the authenticated user's actual role
                        // matches the selected role.
                        //
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/verify-role")
                        .permitAll()

                        // ====================================================
                        // INITIAL ADMIN CREATION
                        // ====================================================
                        //
                        // Preserve your existing behavior.
                        //
                        .requestMatchers(HttpMethod.POST,
                                "/api/admins")
                        .permitAll()

                        // ====================================================
                        // ALL OTHER REQUESTS
                        // ====================================================
                        .anyRequest().authenticated()
                )

                // ------------------------------------------------
                // Exception Handling
                // ------------------------------------------------
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )

                // ------------------------------------------------
                // HTTP BASIC AUTHENTICATION
                // ------------------------------------------------
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

}