package com.example.aichat.api.auth;

import com.example.aichat.domain.user.User;
import com.example.aichat.domain.user.UserRole;
import com.example.aichat.domain.user.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Request/response bodies of the authentication API.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(@NotBlank @Size(min = 3, max = 32) String username,
                                  @NotBlank @Email @Size(max = 255) String email,
                                  @NotBlank @Size(min = 8, max = 128) String password,
                                  @Size(max = 128) String displayName) {

        @Override
        public String toString() {
            return "RegisterRequest{username=" + username + ", email=" + email + ", password=****}";
        }
    }

    public record LoginRequest(@NotBlank @Size(max = 255) String login, @NotBlank @Size(max = 128) String password) {

        @Override
        public String toString() {
            return "LoginRequest{login=" + login + ", password=****}";
        }
    }

    public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserProfile user) {
    }

    public record AuthConfigResponse(boolean registrationEnabled) {
    }

    public record UserProfile(UUID id,
                              String username,
                              String email,
                              String displayName,
                              UserRole role,
                              UserStatus status,
                              Instant createdAt,
                              Instant lastLoginAt) {

        public static UserProfile of(User user) {
            return new UserProfile(user.id(), user.username(), user.email(), user.displayName(), user.role(),
                    user.status(), user.createdAt(), user.lastLoginAt());
        }
    }
}
