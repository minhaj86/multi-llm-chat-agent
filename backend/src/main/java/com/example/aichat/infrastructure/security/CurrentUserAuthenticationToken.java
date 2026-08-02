package com.example.aichat.infrastructure.security;

import com.example.aichat.application.auth.CurrentUser;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authentication whose principal is the application-level {@link CurrentUser}; controllers receive it via
 * {@code @AuthenticationPrincipal}. The raw token is not retained.
 */
public class CurrentUserAuthenticationToken extends AbstractAuthenticationToken {

    private final CurrentUser principal;

    public CurrentUserAuthenticationToken(CurrentUser principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public CurrentUser getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.username();
    }
}
