package com.example.aichat.infrastructure.persistence.entity;

import com.example.aichat.domain.prompt.SystemPrompt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "system_prompts")
public class SystemPromptEntity {

    @Id
    private UUID id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(length = 500)
    private String description;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(name = "is_default", nullable = false)
    private boolean defaultPrompt;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SystemPromptEntity() {
    }

    public static SystemPromptEntity from(SystemPrompt prompt) {
        SystemPromptEntity entity = new SystemPromptEntity();
        entity.id = prompt.id();
        entity.name = prompt.name();
        entity.description = prompt.description();
        entity.content = prompt.content();
        entity.defaultPrompt = prompt.defaultPrompt();
        entity.enabled = prompt.enabled();
        entity.sortOrder = prompt.sortOrder();
        entity.createdAt = prompt.createdAt();
        entity.updatedAt = prompt.updatedAt();
        return entity;
    }

    public SystemPrompt toDomain() {
        return new SystemPrompt(id, name, description, content, defaultPrompt, enabled, sortOrder, createdAt,
                updatedAt);
    }
}
