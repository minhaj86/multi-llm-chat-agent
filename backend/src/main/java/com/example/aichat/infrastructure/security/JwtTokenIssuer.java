package com.example.aichat.infrastructure.security;

import com.example.aichat.application.auth.TokenIssuer;
import com.example.aichat.domain.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Issues signed access tokens: {@code sub} = user id, plus {@code username} and {@code role} claims.
 */
@Component
public class JwtTokenIssuer implements TokenIssuer {

    static final String USERNAME_CLAIM = "username";
    static final String ROLE_CLAIM = "role";

    private final JwtEncoder encoder;
    private final SecuritySettings settings;
    private final Clock clock;

    public JwtTokenIssuer(JwtEncoder encoder, SecuritySettings settings, Clock clock) {
        this.encoder = encoder;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public IssuedToken issue(User user) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(settings.jwt().accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(settings.jwt().issuer())
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(USERNAME_CLAIM, user.username())
                .claim(ROLE_CLAIM, user.role().name())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
