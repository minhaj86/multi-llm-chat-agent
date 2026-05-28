package com.example.aichat.domain.user;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Application user. Passwords are only ever stored as hashes.
 */
public record User(UUID id,
                   String username,
                   String email,
                   String displayName,
                   String passwordHash,
                   UserRole role,
                   UserStatus status,
                   int failedLoginAttempts,
                   Instant lockedUntil,
                   Instant lastLoginAt,
                   Instant createdAt,
                   Instant updatedAt) {

    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
    }

    public static User create(String username, String email, String displayName, String passwordHash,
                              UserRole role, Instant now) {
        return new User(UUID.randomUUID(), username, email.toLowerCase(), displayName, passwordHash, role,
                UserStatus.ACTIVE, 0, null, null, now, now);
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    /** Whether the user may authenticate at {@code now}. Expired lockouts count as active. */
    public boolean canAuthenticate(Instant now) {
        return switch (status) {
            case ACTIVE -> true;
            case DISABLED -> false;
            case LOCKED -> lockedUntil != null && !lockedUntil.isAfter(now);
        };
    }

    public User recordSuccessfulLogin(Instant now) {
        return new User(id, username, email, displayName, passwordHash, role, UserStatus.ACTIVE, 0, null, now,
                createdAt, now);
    }

    public User recordFailedLogin(int maxAttempts, Duration lockoutDuration, Instant now) {
        int attempts = failedLoginAttempts + 1;
        if (status == UserStatus.ACTIVE && attempts >= maxAttempts) {
            return new User(id, username, email, displayName, passwordHash, role, UserStatus.LOCKED, attempts,
                    now.plus(lockoutDuration), lastLoginAt, createdAt, now);
        }
        return new User(id, username, email, displayName, passwordHash, role, status, attempts, lockedUntil,
                lastLoginAt, createdAt, now);
    }

    public User disable(Instant now) {
        return new User(id, username, email, displayName, passwordHash, role, UserStatus.DISABLED,
                failedLoginAttempts, lockedUntil, lastLoginAt, createdAt, now);
    }

    /** Re-activates a disabled or locked account and clears failed login attempts. */
    public User resetStatus(Instant now) {
        return new User(id, username, email, displayName, passwordHash, role, UserStatus.ACTIVE, 0, null,
                lastLoginAt, createdAt, now);
    }

    public User withRole(UserRole newRole, Instant now) {
        return new User(id, username, email, displayName, passwordHash, newRole, status, failedLoginAttempts,
                lockedUntil, lastLoginAt, createdAt, now);
    }
}
