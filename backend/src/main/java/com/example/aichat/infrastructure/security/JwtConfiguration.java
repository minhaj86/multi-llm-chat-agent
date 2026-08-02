package com.example.aichat.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * HS256 JWT signing/verification (Nimbus) and BCrypt password hashing.
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JwtConfiguration.class);
    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    SecretKey jwtSigningKey(SecuritySettings settings) {
        String secret = settings.jwt().secret();
        byte[] bytes;
        if (secret == null || secret.isBlank()) {
            bytes = new byte[64];
            new SecureRandom().nextBytes(bytes);
            log.warn("JWT_SECRET is not set: using a random signing key. Issued tokens become invalid on restart. "
                    + "Set JWT_SECRET (>= 32 bytes) in production.");
        } else {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes long");
            }
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, SecuritySettings settings) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(settings.jwt().issuer()));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder(SecuritySettings settings) {
        return new BCryptPasswordEncoder(settings.bcryptStrength());
    }
}
