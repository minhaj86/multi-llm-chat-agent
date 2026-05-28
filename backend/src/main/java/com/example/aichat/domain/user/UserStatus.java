package com.example.aichat.domain.user;

public enum UserStatus {
    ACTIVE,
    /** Disabled by an administrator. */
    DISABLED,
    /** Temporarily locked after too many failed logins. */
    LOCKED
}
