package com.example.aichat.infrastructure.persistence.entity;

import com.example.aichat.domain.user.User;
import com.example.aichat.domain.user.UserRole;
import com.example.aichat.domain.user.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;
    @Column(nullable = false, length = 64)
    private String username;
    @Column(nullable = false)
    private String email;
    @Column(name = "display_name", length = 128)
    private String displayName;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole role;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status;
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;
    @Column(name = "locked_until")
    private Instant lockedUntil;
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
    }

    public static UserEntity from(User user) {
        UserEntity entity = new UserEntity();
        entity.id = user.id();
        entity.username = user.username();
        entity.email = user.email();
        entity.displayName = user.displayName();
        entity.passwordHash = user.passwordHash();
        entity.role = user.role();
        entity.status = user.status();
        entity.failedLoginAttempts = user.failedLoginAttempts();
        entity.lockedUntil = user.lockedUntil();
        entity.lastLoginAt = user.lastLoginAt();
        entity.createdAt = user.createdAt();
        entity.updatedAt = user.updatedAt();
        return entity;
    }

    public User toDomain() {
        return new User(id, username, email, displayName, passwordHash, role, status, failedLoginAttempts, lockedUntil,
                lastLoginAt, createdAt, updatedAt);
    }
}
