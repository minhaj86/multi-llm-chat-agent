package com.example.aichat.application.provider;

/**
 * The provider exists but has been disabled by configuration or by an administrator.
 */
public class ProviderDisabledException extends RuntimeException {

    private final String providerId;

    public ProviderDisabledException(String providerId, String displayName) {
        super(displayName + " is disabled");
        this.providerId = providerId;
    }

    public String providerId() {
        return providerId;
    }
}
