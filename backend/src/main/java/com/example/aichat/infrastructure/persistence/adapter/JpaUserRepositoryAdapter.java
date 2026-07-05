package com.example.aichat.infrastructure.persistence.adapter;

import com.example.aichat.domain.user.User;
import com.example.aichat.domain.user.UserRepository;
import com.example.aichat.domain.user.UserRole;
import com.example.aichat.infrastructure.persistence.entity.UserEntity;
import com.example.aichat.infrastructure.persistence.jpa.UserJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaUserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpa;

    public JpaUserRepositoryAdapter(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public User save(User user) {
        return jpa.save(UserEntity.from(user)).toDomain();
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findByLogin(String usernameOrEmail) {
        return jpa.findByLogin(usernameOrEmail).stream().findFirst().map(UserEntity::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpa.existsByUsernameIgnoreCase(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmailIgnoreCase(email);
    }

    @Override
    public List<User> findAll() {
        return jpa.findAll().stream().map(UserEntity::toDomain).toList();
    }

    @Override
    public long countByRole(UserRole role) {
        return jpa.countByRole(role);
    }

    @Override
    public long count() {
        return jpa.count();
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }
}
