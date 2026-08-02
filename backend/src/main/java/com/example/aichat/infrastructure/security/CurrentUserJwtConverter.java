package com.example.aichat.infrastructure.security;

import com.example.aichat.application.auth.CurrentUser;
import com.example.aichat.application.user.UserAccountChangedEvent;
import com.example.aichat.domain.user.User;
import com.example.aichat.domain.user.UserRepository;
import com.example.aichat.domain.user.UserRole;
import com.example.aichat.domain.user.UserStatus;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Turns a validated JWT into an authenticated {@link CurrentUser}. The user's current status and role are loaded
 * from the database (cached briefly), so disabling or deleting an account revokes its tokens immediately rather
 * than at token expiry. Temporarily locked accounts keep existing sessions (locking only guards password login).
 */
@Component
public class CurrentUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;
    private final Cache<UUID, Optional<AccountSnapshot>> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .maximumSize(50_000)
            .build();

    public CurrentUserJwtConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("Invalid subject");
        }
        AccountSnapshot account = cache.get(userId, id -> users.findById(id).map(AccountSnapshot::of))
                .orElseThrow(() -> new InvalidBearerTokenException("Unknown user"));
        if (account.status() == UserStatus.DISABLED) {
            throw new DisabledException("Account is disabled");
        }
        CurrentUser principal = new CurrentUser(userId, account.username(), account.role());
        return new CurrentUserAuthenticationToken(principal,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.role().name())));
    }

    @EventListener
    public void onAccountChanged(UserAccountChangedEvent event) {
        cache.invalidate(event.userId());
    }

    private record AccountSnapshot(String username, UserRole role, UserStatus status) {
        static AccountSnapshot of(User user) {
            return new AccountSnapshot(user.username(), user.role(), user.status());
        }
    }
}
