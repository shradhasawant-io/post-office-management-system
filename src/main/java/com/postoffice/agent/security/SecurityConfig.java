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

    public SecurityConfig(CustomUserDetailsService customUserDetailsService, PasswordEncoder passwordEncoder) {
        this.customUserDetailsService = customUserDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    /*
     * Authentication provider
     *
     * UserDetailsService
     *        +
     * PasswordEncoder
     *        ↓
     * DaoAuthenticationProvider
     */
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

    //HTTP Security configuration
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth

                        // Allow creation of the first admin without authentication
                        .requestMatchers(HttpMethod.POST, "/api/admins").permitAll()

                        // All other admin operations require ADMIN role
                        .requestMatchers("/api/admins/**").hasRole("ADMIN")

                        // Only ADMIN can manage agents
                        .requestMatchers("/api/agents/**").hasRole("ADMIN")

                        // AGENT business operations
                        //.requestMatchers("/api/customers/**").hasRole("AGENT")

                        // ADMIN endpoints
                        .requestMatchers("/api/customers/agents/**").hasRole("ADMIN")
                        .requestMatchers("/api/customers/**").authenticated()
                        .requestMatchers("/api/accounts/**").authenticated()
                        .requestMatchers("/api/accounts/agents/**").hasRole("ADMIN")

                        // Admin read-only business data
                        .requestMatchers(HttpMethod.GET, "/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/agents/**").hasRole("ADMIN")

                        // ADMIN business-data access
                        .requestMatchers("/api/transactions/admin/agents/**").hasRole("ADMIN")

                        //.requestMatchers("/api/accounts/**").hasRole("AGENT")
                        .requestMatchers("/api/transactions/**").authenticated()

                        // All remaining endpoints require authentication
                        .anyRequest().authenticated()
                )

                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

}
