package com.ordercraft.ordercraft.controller;

import com.ordercraft.ordercraft.entity.AppUser;
import com.ordercraft.ordercraft.service.AuthService;
import com.ordercraft.ordercraft.service.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:4200")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(
            AuthService authService,
            JwtService jwtService
    ) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    // =========================================================
    // REGISTER
    // =========================================================

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<?> register(
            @RequestBody AuthRequest request
    ) {
        requireRequest(request);
        AppUser user = authService.register(
                request.email(),
                request.password(),
                "USER"
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new AuthResponse(
                        "Registration successful",
                        user.getId(),
                        user.getEmail(),
                        user.getRole(),
                        null
                ));
    }

    // =========================================================
    // LOGIN
    // =========================================================

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody AuthRequest request
    ) {
        requireRequest(request);
        AppUser user = authService.login(
                request.email(),
                request.password()
        );
        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new AuthResponse(
                "Login successful",
                user.getId(),
                user.getEmail(),
                user.getRole(),
                token
        ));
    }

    @GetMapping("/me")
    public UserResponse currentUser(Authentication authentication) {
        AppUser user = authService.getAuthenticatedUser(authentication.getName());
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }

    private void requireRequest(AuthRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authentication data is required");
        }
    }

    // =========================================================
    // REQUEST
    // =========================================================

    public record AuthRequest(
            String email,
            String password
    ) {
    }

    // =========================================================
    // SUCCESS RESPONSE
    // =========================================================

    public record AuthResponse(
            String message,
            Long userId,
            String email,
            String role,
            String token
    ) {
    }

    public record UserResponse(Long userId, String email, String role) {
    }

}
