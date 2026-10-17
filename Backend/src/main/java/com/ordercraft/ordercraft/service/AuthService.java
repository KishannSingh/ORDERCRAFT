package com.ordercraft.ordercraft.service;

import com.ordercraft.ordercraft.entity.AppUser;
import com.ordercraft.ordercraft.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AppUser register(
            String email,
            String password,
            String role
    ) {

        if (appUserRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already registered");
        }

        AppUser user = new AppUser();

        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);

        return appUserRepository.save(user);
    }


    public AppUser login(
            String email,
            String password
    ) {

        AppUser user = appUserRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("Invalid email or password")
                );

        if (!passwordEncoder.matches(
                password,
                user.getPassword()
        )) {

            throw new RuntimeException(
                    "Invalid email or password"
            );
        }

        return user;
    }
}