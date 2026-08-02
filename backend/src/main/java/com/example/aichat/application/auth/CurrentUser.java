package com.example.aichat.application.auth;

import com.example.aichat.domain.user.UserRole;
import java.util.UUID;

/**
 * The authenticated caller, resolved from the access token.
 */
public record CurrentUser(UUID id, String username, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
