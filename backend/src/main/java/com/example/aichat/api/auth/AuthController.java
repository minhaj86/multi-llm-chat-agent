package com.example.aichat.api.auth;

import com.example.aichat.api.auth.AuthDtos.AuthConfigResponse;
import com.example.aichat.api.auth.AuthDtos.AuthResponse;
import com.example.aichat.api.auth.AuthDtos.LoginRequest;
import com.example.aichat.api.auth.AuthDtos.RegisterRequest;
import com.example.aichat.api.auth.AuthDtos.UserProfile;
import com.example.aichat.application.auth.AuthService;
import com.example.aichat.application.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/config")
    public AuthConfigResponse config() {
        return new AuthConfigResponse(authService.registrationEnabled());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return toResponse(authService.register(new AuthService.RegistrationCommand(request.username(),
                request.email(), request.password(), request.displayName())));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return toResponse(authService.login(request.login(), request.password()));
    }

    @GetMapping("/me")
    public UserProfile me(@AuthenticationPrincipal CurrentUser user) {
        return UserProfile.of(authService.profile(user.id()));
    }

    private static AuthResponse toResponse(AuthService.AuthResult result) {
        return new AuthResponse(result.token().value(), "Bearer", result.token().expiresAt(),
                UserProfile.of(result.user()));
    }
}
