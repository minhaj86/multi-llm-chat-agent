package com.example.aichat.rag.application;

import com.example.aichat.application.common.ConflictException;
import com.example.aichat.application.common.ResourceNotFoundException;
import com.example.aichat.application.common.ValidationException;
import com.example.aichat.common.Times;
import com.example.aichat.rag.domain.IngestionStatus;
import com.example.aichat.rag.domain.KnowledgeChunkRepository;
import com.example.aichat.rag.domain.KnowledgeContentRepository;
import com.example.aichat.rag.domain.KnowledgeFileStore;
import com.example.aichat.rag.domain.KnowledgeSource;
import com.example.aichat.rag.domain.KnowledgeSourceRepository;
import com.example.aichat.rag.domain.KnowledgeSourceRepository.ContentReplacement;
import com.example.aichat.rag.domain.KnowledgeSourceType;
import com.example.aichat.rag.domain.VectorStore;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Writes knowledge sources for both file uploads and manual text: duplicate handling, content replacement and
 * metadata changes. Every change that affects retrieval updates the vector store in the same transaction;
 * indexing is scheduled after commit.
 */
@Component
public class KnowledgeSourceWriter {

    private final KnowledgeSourceRepository sources;
    private final KnowledgeChunkRepository chunks;
    private final KnowledgeContentRepository contents;
    private final KnowledgeFileStore files;
    private final VectorStore vectorStore;
    private final IngestionQueue queue;
    private final TransactionOperations transactions;
    private final Clock clock;

    public KnowledgeSourceWriter(KnowledgeSourceRepository sources, KnowledgeChunkRepository chunks,
                                 KnowledgeContentRepository contents, KnowledgeFileStore files,
                                 VectorStore vectorStore, IngestionQueue queue, TransactionOperations transactions,
                                 Clock clock) {
        this.sources = sources;
        this.chunks = chunks;
        this.contents = contents;
        this.files = files;
        this.vectorStore = vectorStore;
        this.queue = queue;
        this.transactions = transactions;
        this.clock = clock;
    }

    public IngestionResult ingest(NewContent content, SourceMetadata metadata, UUID userId,
                                  DuplicateStrategy strategy, UUID replaceSourceId) {
        if (replaceSourceId != null) {
            return replace(require(replaceSourceId), content, metadata);
        }
        List<KnowledgeSource> duplicates = sources.findByChecksum(content.checksum());
        if (!duplicates.isEmpty()) {
            return switch (strategy == null ? DuplicateStrategy.REJECT : strategy) {
                case REJECT -> throw new DuplicateKnowledgeException(duplicates);
                case REPLACE -> replace(duplicates.getFirst(), content, metadata);
                case NEW_VERSION -> create(content, metadata, userId, duplicates.getFirst());
            };
        }
        return create(content, metadata, userId, null);
    }

    /** New content for an existing source: previous chunks and vectors are removed at once, then it is reindexed. */
    public IngestionResult replace(KnowledgeSource target, NewContent content, SourceMetadata requested) {
        if (target.type() != content.type()) {
            throw new ValidationException("TYPE_MISMATCH", "A " + target.type().name().toLowerCase()
                    + " source can only be replaced with the same kind of content");
        }
        SourceMetadata metadata = requested.over(target);
        Instant now = Times.now(clock);
        int version = target.version() + 1;
        transactions.executeWithoutResult(tx -> {
            lockForChange(target.id());
            vectorStore.deleteBySourceId(target.id());
            chunks.deleteBySourceId(target.id());
            contents.delete(target.id());
            files.save(target.id(), content.bytes());
            sources.replaceContent(target.id(), new ContentReplacement(metadata.title(), content.filename(),
                    content.mimeType(), content.checksum(), content.bytes().length, version), now);
            sources.updateMetadata(target.id(), metadata.title(), metadata.collectionId(), metadata.tags(),
                    metadata.sourceUrl(), now);
        });
        queue.submit(target.id(), true);
        return new IngestionResult(target.id(), IngestionStatus.UPLOADED, version, target.id(), true);
    }

    public void updateMetadata(UUID id, SourceMetadata metadata) {
        Instant now = Times.now(clock);
        transactions.executeWithoutResult(tx -> {
            lockForChange(id);
            KnowledgeSource current = require(id);
            sources.updateMetadata(id, metadata.title(), metadata.collectionId(), metadata.tags(),
                    metadata.sourceUrl(), now);
            if (!current.collectionId().equals(metadata.collectionId())) {
                chunks.updateCollection(id, metadata.collectionId());
            }
            vectorStore.updateSourceMetadata(id, metadata.collectionId(), current.enabled(), metadata.tags());
        });
    }

    public void setEnabled(UUID id, boolean enabled) {
        Instant now = Times.now(clock);
        transactions.executeWithoutResult(tx -> {
            lockForChange(id);
            KnowledgeSource current = require(id);
            sources.updateEnabled(id, enabled, now);
            vectorStore.updateSourceMetadata(id, current.collectionId(), enabled, current.tags());
        });
    }

    private IngestionResult create(NewContent content, SourceMetadata metadata, UUID userId,
                                   KnowledgeSource previousVersion) {
        UUID id = UUID.randomUUID();
        Instant now = Times.now(clock);
        int version = previousVersion == null ? 1 : previousVersion.version() + 1;
        KnowledgeSource source = new KnowledgeSource(id, content.type(), metadata.title(), content.filename(),
                content.mimeType(), metadata.sourceUrl(), metadata.collectionId(), IngestionStatus.UPLOADED, true,
                metadata.tags(), content.checksum(), content.bytes().length, null, version,
                previousVersion == null ? null : previousVersion.id(), userId, now, now, null, null, null, 0, null,
                Map.of());
        transactions.executeWithoutResult(tx -> {
            sources.insert(source);
            files.save(id, content.bytes());
            if (previousVersion != null && previousVersion.enabled()) {
                sources.updateEnabled(previousVersion.id(), false, now);
                vectorStore.updateSourceMetadata(previousVersion.id(), previousVersion.collectionId(), false,
                        previousVersion.tags());
            }
        });
        queue.submit(id, true);
        return new IngestionResult(id, IngestionStatus.UPLOADED, version,
                previousVersion == null ? null : previousVersion.id(), false);
    }

    private void lockForChange(UUID id) {
        IngestionStatus status = sources.lockStatus(id)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge source", id));
        if (status == IngestionStatus.DELETING) {
            throw new ConflictException("SOURCE_DELETING", "The knowledge source is being deleted");
        }
    }

    private KnowledgeSource require(UUID id) {
        return sources.findById(id).orElseThrow(() -> new ResourceNotFoundException("Knowledge source", id));
    }

    /** Content to store: original bytes (files) or UTF-8 Markdown (manual text). */
    public record NewContent(KnowledgeSourceType type, String filename, String mimeType, byte[] bytes,
                             String checksum) {
    }

    /**
     * Metadata of new or replaced content. {@code provided} lists the fields the administrator actually sent: when
     * the content replaces an existing source, the other fields keep that source's current values (new sources use
     * the resolved defaults held in the record).
     */
    public record SourceMetadata(String title, UUID collectionId, List<String> tags, String sourceUrl,
                                 Set<Field> provided) {

        public enum Field { TITLE, COLLECTION, TAGS, SOURCE_URL }

        public SourceMetadata {
            provided = provided == null ? Set.of(Field.values()) : Set.copyOf(provided);
        }

        /** Complete metadata: every field is applied, also to a replaced source. */
        public SourceMetadata(String title, UUID collectionId, List<String> tags, String sourceUrl) {
            this(title, collectionId, tags, sourceUrl, null);
        }

        SourceMetadata over(KnowledgeSource current) {
            return new SourceMetadata(
                    provided.contains(Field.TITLE) ? title : current.title(),
                    provided.contains(Field.COLLECTION) ? collectionId : current.collectionId(),
                    provided.contains(Field.TAGS) ? tags : current.tags(),
                    provided.contains(Field.SOURCE_URL) ? sourceUrl : current.sourceUrl());
        }
    }

    /**
     * @param relatedSourceId replaced source, or previous version for a new version
     */
    public record IngestionResult(UUID sourceId, IngestionStatus status, int version, UUID relatedSourceId,
                                  boolean replaced) {
    }
}
