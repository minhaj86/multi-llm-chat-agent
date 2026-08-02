package com.example.aichat.application.auth;

import com.example.aichat.application.common.ConflictException;
import com.example.aichat.application.common.ValidationException;
import com.example.aichat.common.Texts;
import com.example.aichat.domain.user.UserRepository;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Shared validation and normalisation rules for user accounts.
 */
@Component
public class AccountValidator {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,32}");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$");
    static final int MIN_PASSWORD_LENGTH = 8;
    static final int MAX_PASSWORD_LENGTH = 128;

    private final UserRepository users;

    public AccountValidator(UserRepository users) {
        this.users = users;
    }

    public String normalizeUsername(String username) {
        String value = username == null ? "" : username.strip();
        if (!USERNAME.matcher(value).matches()) {
            throw new ValidationException("INVALID_USERNAME",
                    "Username must be 3-32 characters (letters, digits, '.', '_' or '-')");
        }
        return value;
    }

    public String normalizeEmail(String email) {
        String value = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (value.length() > 255 || !EMAIL.matcher(value).matches()) {
            throw new ValidationException("INVALID_EMAIL", "A valid e-mail address is required");
        }
        return value;
    }

    public String normalizeDisplayName(String displayName, String fallback) {
        String value = Texts.blankToNull(displayName);
        if (value == null) {
            return fallback;
        }
        return Texts.truncate(Texts.singleLine(value), 128);
    }

    public void validatePassword(String password, String username) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new ValidationException("WEAK_PASSWORD", "Password must be between " + MIN_PASSWORD_LENGTH
                    + " and " + MAX_PASSWORD_LENGTH + " characters");
        }
        if (password.isBlank() || (username != null && password.equalsIgnoreCase(username))) {
            throw new ValidationException("WEAK_PASSWORD", "Password must not be blank or equal to the username");
        }
    }

    public void ensureUnique(String username, String email) {
        if (users.existsByUsername(username)) {
            throw new ConflictException("USERNAME_TAKEN", "Username is already taken");
        }
        if (users.existsByEmail(email)) {
            throw new ConflictException("EMAIL_TAKEN", "E-mail address is already registered");
        }
    }
}
