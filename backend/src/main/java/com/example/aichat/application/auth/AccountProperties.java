package com.example.aichat.application.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Account policy.
 *
 * @param registrationEnabled whether self-service registration is allowed
 * @param firstUserAdmin      the first registered user becomes administrator when no administrator exists
 * @param maxFailedLogins     failed attempts before the account is temporarily locked
 * @param lockoutDuration     how long a locked account stays locked (administrators can reset earlier)
 * @param bootstrapAdmin      optional administrator created at startup
 */
@ConfigurationProperties("app.accounts")
public record AccountProperties(@DefaultValue("true") boolean registrationEnabled,
                                @DefaultValue("true") boolean firstUserAdmin,
                                @DefaultValue("5") int maxFailedLogins,
                                @DefaultValue("PT15M") Duration lockoutDuration,
                                @DefaultValue BootstrapAdmin bootstrapAdmin) {

    public record BootstrapAdmin(String username, String password, @DefaultValue("admin@localhost") String email) {
    }
}
