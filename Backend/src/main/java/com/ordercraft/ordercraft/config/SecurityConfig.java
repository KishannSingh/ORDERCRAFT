package com.ordercraft.ordercraft.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(auth -> auth

                        // Login and registration
                        .requestMatchers("/api/auth/**").permitAll()

                        // Customers
                        .requestMatchers(
                                "/customers", "/customers/**",
                                "/api/customers", "/api/customers/**"
                        ).permitAll()

                        // Products
                        .requestMatchers(
                                "/products", "/products/**"
                        ).permitAll()

                        // Bill of Materials
                        .requestMatchers(
                                "/boms", "/boms/**",
                                "/bom", "/bom/**"
                        ).permitAll()

                        // Sales Orders
                        .requestMatchers(
                                "/orders", "/orders/**"
                        ).permitAll()

                        // Purchase Orders
                        .requestMatchers(
                                "/purchase-orders", "/purchase-orders/**"
                        ).permitAll()

                        // Invoices
                        .requestMatchers(
                                "/invoices", "/invoices/**"
                        ).permitAll()

                        // Payments
                        .requestMatchers(
                                "/payments", "/payments/**"
                        ).permitAll()

                        // Other endpoints
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}