package com.example.aichat.rag.application;

import com.example.aichat.application.common.ConflictException;
import com.example.aichat.rag.domain.KnowledgeSource;
import java.util.List;
import java.util.UUID;

/**
 * The uploaded content already exists in the knowledge base. The client can cancel, upload it as a new version
 * (previous version is disabled) or replace the existing source.
 */
public class DuplicateKnowledgeException extends ConflictException {

    private final transient List<Existing> existing;

    public DuplicateKnowledgeException(List<KnowledgeSource> duplicates) {
        super("DUPLICATE_DOCUMENT", "This document already exists in the knowledge base.");
        this.existing = duplicates.stream().map(s -> new Existing(s.id(), s.title(), s.collectionId(), s.version(),
                s.status().name(), s.enabled())).toList();
    }

    public List<Existing> existing() {
        return existing;
    }

    public record Existing(UUID id, String title, UUID collectionId, int version, String status, boolean enabled) {
    }
}
