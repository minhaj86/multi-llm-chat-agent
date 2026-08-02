package com.example.aichat.application.user;

import com.example.aichat.application.auth.AccountProperties;
import com.example.aichat.application.auth.AccountValidator;
import com.example.aichat.application.auth.PasswordHasher;
import com.example.aichat.application.common.ValidationException;
import com.example.aichat.common.Texts;
import com.example.aichat.common.Times;
import com.example.aichat.domain.user.User;
import com.example.aichat.domain.user.UserRepository;
import com.example.aichat.domain.user.UserRole;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the administrator configured via {@code ADMIN_USERNAME}/{@code ADMIN_PASSWORD} on first start.
 * Existing accounts are never modified (the password is not reset on restart).
 */
@Component
public class AdminBootstrapper implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapper.class);

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final AccountValidator validator;
    private final AccountProperties properties;
    private final Clock clock;

    public AdminBootstrapper(UserRepository users, PasswordHasher passwordHasher, AccountValidator validator,
                             AccountProperties properties, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.validator = validator;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        AccountProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (admin == null || Texts.isBlank(admin.username()) || Texts.isBlank(admin.password())) {
            if (users.countByRole(UserRole.ADMIN) == 0) {
                log.info("No administrator exists yet. Set ADMIN_USERNAME/ADMIN_PASSWORD or register the first user "
                        + "(app.accounts.first-user-admin={})", properties.firstUserAdmin());
            }
            return;
        }
        try {
            String username = validator.normalizeUsername(admin.username());
            if (users.existsByUsername(username)) {
                return;
            }
            String email = validator.normalizeEmail(admin.email());
            if (users.existsByEmail(email)) {
                log.warn("Bootstrap administrator not created: e-mail {} is already in use", email);
                return;
            }
            validator.validatePassword(admin.password(), username);
            users.save(User.create(username, email, username, passwordHasher.hash(admin.password()), UserRole.ADMIN,
                    Times.now(clock)));
            log.info("Bootstrap administrator '{}' created", username);
        } catch (ValidationException e) {
            throw new IllegalStateException("Invalid bootstrap administrator (ADMIN_USERNAME/ADMIN_PASSWORD/"
                    + "ADMIN_EMAIL): " + e.getMessage(), e);
        }
    }
}
