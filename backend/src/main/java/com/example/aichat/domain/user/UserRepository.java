package com.example.aichat.domain.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for users.
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    /** Case-insensitive lookup by username or e-mail address. */
    Optional<User> findByLogin(String usernameOrEmail);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findAll();

    long countByRole(UserRole role);

    long count();

    void deleteById(UUID id);
}
