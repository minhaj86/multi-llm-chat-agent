package com.example.aichat.application.user;

import com.example.aichat.application.auth.AccountValidator;
import com.example.aichat.application.auth.PasswordHasher;
import com.example.aichat.application.common.ConflictException;
import com.example.aichat.application.common.ResourceNotFoundException;
import com.example.aichat.common.Times;
import com.example.aichat.domain.user.User;
import com.example.aichat.domain.user.UserRepository;
import com.example.aichat.domain.user.UserRole;
import com.example.aichat.domain.user.UserStatus;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User administration. Guards against administrators locking themselves (or everybody) out.
 */
@Service
public class AdminUserService {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final AccountValidator validator;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AdminUserService(UserRepository users, PasswordHasher passwordHasher, AccountValidator validator,
                            ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.validator = validator;
        this.events = events;
        this.clock = clock;
    }

    public List<User> list() {
        return users.findAll().stream().sorted(Comparator.comparing(User::createdAt)).toList();
    }

    @Transactional
    public User create(CreateUserCommand command) {
        String username = validator.normalizeUsername(command.username());
        String email = validator.normalizeEmail(command.email());
        validator.validatePassword(command.password(), username);
        validator.ensureUnique(username, email);
        UserRole role = command.role() == null ? UserRole.USER : command.role();
        return users.save(User.create(username, email, validator.normalizeDisplayName(command.displayName(), username),
                passwordHasher.hash(command.password()), role, Times.now(clock)));
    }

    @Transactional
    public User disable(UUID actorId, UUID userId) {
        User user = get(userId);
        if (user.id().equals(actorId)) {
            throw new ConflictException("CANNOT_DISABLE_SELF", "You cannot disable your own account");
        }
        ensureNotLastActiveAdmin(user);
        return changed(users.save(user.disable(Times.now(clock))));
    }

    /** Re-activates a disabled or locked account and clears failed login attempts. */
    @Transactional
    public User resetStatus(UUID userId) {
        return changed(users.save(get(userId).resetStatus(Times.now(clock))));
    }

    @Transactional
    public User changeRole(UUID actorId, UUID userId, UserRole role) {
        User user = get(userId);
        if (user.id().equals(actorId) && role != UserRole.ADMIN) {
            throw new ConflictException("CANNOT_DEMOTE_SELF", "You cannot remove your own administrator role");
        }
        if (user.isAdmin() && role != UserRole.ADMIN) {
            ensureNotLastActiveAdmin(user);
        }
        return changed(users.save(user.withRole(role, Times.now(clock))));
    }

    @Transactional
    public void delete(UUID actorId, UUID userId) {
        User user = get(userId);
        if (user.id().equals(actorId)) {
            throw new ConflictException("CANNOT_DELETE_SELF", "You cannot delete your own account");
        }
        ensureNotLastActiveAdmin(user);
        users.deleteById(userId);
        events.publishEvent(new UserAccountChangedEvent(userId));
    }

    private User changed(User user) {
        events.publishEvent(new UserAccountChangedEvent(user.id()));
        return user;
    }

    private User get(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private void ensureNotLastActiveAdmin(User user) {
        if (!user.isAdmin() || user.status() != UserStatus.ACTIVE) {
            return;
        }
        long activeAdmins = users.findAll().stream()
                .filter(u -> u.isAdmin() && u.status() == UserStatus.ACTIVE)
                .count();
        if (activeAdmins <= 1) {
            throw new ConflictException("LAST_ADMIN", "At least one active administrator is required");
        }
    }

    public record CreateUserCommand(String username, String email, String password, String displayName,
                                    UserRole role) {
    }
}
