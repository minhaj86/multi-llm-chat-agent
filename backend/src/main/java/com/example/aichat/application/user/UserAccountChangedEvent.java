package com.example.aichat.application.user;

import java.util.UUID;

/**
 * Published when an account's status, role or existence changes, so that authentication caches can be evicted and
 * the change takes effect immediately for already issued tokens.
 */
public record UserAccountChangedEvent(UUID userId) {
}
