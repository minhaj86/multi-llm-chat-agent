package com.example.aichat.rag.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Keeps the original bytes of uploaded files, needed to retry a failed extraction. The database implementation
 * keeps everything transactional; object storage (S3, Azure Blob) can implement the same port.
 */
public interface KnowledgeFileStore {

    void save(UUID sourceId, byte[] content);

    Optional<byte[]> load(UUID sourceId);

    void delete(UUID sourceId);
}
