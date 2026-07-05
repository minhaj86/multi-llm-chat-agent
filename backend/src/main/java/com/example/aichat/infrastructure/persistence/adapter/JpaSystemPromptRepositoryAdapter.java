package com.example.aichat.infrastructure.persistence.adapter;

import com.example.aichat.domain.prompt.SystemPrompt;
import com.example.aichat.domain.prompt.SystemPromptRepository;
import com.example.aichat.infrastructure.persistence.entity.SystemPromptEntity;
import com.example.aichat.infrastructure.persistence.jpa.SystemPromptJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaSystemPromptRepositoryAdapter implements SystemPromptRepository {

    private final SystemPromptJpaRepository jpa;

    public JpaSystemPromptRepositoryAdapter(SystemPromptJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public SystemPrompt save(SystemPrompt prompt) {
        return jpa.save(SystemPromptEntity.from(prompt)).toDomain();
    }

    @Override
    public Optional<SystemPrompt> findById(UUID id) {
        return jpa.findById(id).map(SystemPromptEntity::toDomain);
    }

    @Override
    public List<SystemPrompt> findAll() {
        return jpa.findAllOrdered().stream().map(SystemPromptEntity::toDomain).toList();
    }

    @Override
    public Optional<SystemPrompt> findDefault() {
        return jpa.findDefault().map(SystemPromptEntity::toDomain);
    }

    @Override
    public boolean existsByNameIgnoreCase(String name, UUID excludingId) {
        return excludingId == null ? jpa.existsByName(name) : jpa.existsByNameExcluding(name, excludingId);
    }

    @Override
    @Transactional
    public void clearDefault() {
        jpa.clearDefault();
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }
}
