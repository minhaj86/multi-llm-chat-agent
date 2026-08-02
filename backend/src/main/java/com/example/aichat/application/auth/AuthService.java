package com.example.aichat.application.auth;

import com.example.aichat.application.common.ResourceNotFoundException;
import com.example.aichat.application.common.ValidationException;
import com.example.aichat.common.Times;
import com.example.aichat.domain.user.User;
import com.example.aichat.domain.user.UserRepository;
import com.example.aichat.domain.user.UserRole;
import com.example.aichat.domain.user.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and login.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final AccountValidator validator;
    private final AccountProperties properties;
    private final Clock clock;
    private volatile String dummyHash;

    public AuthService(UserRepository users, PasswordHasher passwordHasher, TokenIssuer tokenIssuer,
                       AccountValidator validator, AccountProperties properties, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.validator = validator;
        this.properties = properties;
        this.clock = clock;
    }

    public boolean registrationEnabled() {
        return properties.registrationEnabled();
    }

    @Transactional
    public AuthResult register(RegistrationCommand command) {
        if (!properties.registrationEnabled()) {
            throw new ValidationException("REGISTRATION_DISABLED", "Self-service registration is disabled");
        }
        String username = validator.normalizeUsername(command.username());
        String email = validator.normalizeEmail(command.email());
        validator.validatePassword(command.password(), username);
        validator.ensureUnique(username, email);
        UserRole role = properties.firstUserAdmin() && users.countByRole(UserRole.ADMIN) == 0
                ? UserRole.ADMIN
                : UserRole.USER;
        User user = users.save(User.create(username, email, validator.normalizeDisplayName(command.displayName(), username),
                passwordHasher.hash(command.password()), role, Times.now(clock)));
        return new AuthResult(tokenIssuer.issue(user), user);
    }

    /**
     * Authenticates by username or e-mail. Failed attempts are counted and lock the account temporarily.
     * Unknown users still cost one hash comparison to avoid user enumeration via timing.
     */
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountUnavailableException.class})
    public AuthResult login(String login, String password) {
        if (login == null || login.isBlank() || password == null) {
            throw new InvalidCredentialsException();
        }
        User user = users.findByLogin(login.strip()).orElse(null);
        if (user == null) {
            passwordHasher.matches(password, dummyHash());
            throw new InvalidCredentialsException();
        }
        Instant now = Times.now(clock);
        if (!user.canAuthenticate(now)) {
            if (user.status() == UserStatus.DISABLED) {
                throw new AccountUnavailableException("ACCOUNT_DISABLED", "This account has been disabled");
            }
            throw new AccountUnavailableException("ACCOUNT_LOCKED",
                    "This account is temporarily locked after too many failed logins");
        }
        if (!passwordHasher.matches(password, user.passwordHash())) {
            users.save(user.recordFailedLogin(properties.maxFailedLogins(), properties.lockoutDuration(), now));
            throw new InvalidCredentialsException();
        }
        User updated = users.save(user.recordSuccessfulLogin(now));
        return new AuthResult(tokenIssuer.issue(updated), updated);
    }

    public User profile(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private String dummyHash() {
        String hash = dummyHash;
        if (hash == null) {
            hash = passwordHasher.hash(UUID.randomUUID().toString());
            dummyHash = hash;
        }
        return hash;
    }

    public record RegistrationCommand(String username, String email, String password, String displayName) {
    }

    public record AuthResult(TokenIssuer.IssuedToken token, User user) {
    }
}
