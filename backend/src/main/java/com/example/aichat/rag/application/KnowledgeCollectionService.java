package com.example.aichat.rag.application;

import com.example.aichat.application.common.ConflictException;
import com.example.aichat.application.common.ResourceNotFoundException;
import com.example.aichat.application.common.ValidationException;
import com.example.aichat.common.Times;
import com.example.aichat.rag.domain.KnowledgeCollection;
import com.example.aichat.rag.domain.KnowledgeCollectionRepository;
import com.example.aichat.rag.domain.KnowledgeCollectionRepository.CollectionUsage;
import com.example.aichat.rag.domain.VectorStore;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Knowledge collections. Deleting a collection removes its sources with all chunks and vectors; the default
 * collection cannot be deleted. Disabling a collection only excludes it from retrieval.
 */
@Service
public class KnowledgeCollectionService {

    private final KnowledgeCollectionRepository collections;
    private final VectorStore vectorStore;
    private final Clock clock;

    public KnowledgeCollectionService(KnowledgeCollectionRepository collections, VectorStore vectorStore,
                                      Clock clock) {
        this.collections = collections;
        this.vectorStore = vectorStore;
        this.clock = clock;
    }

    public List<CollectionView> list() {
        Map<UUID, CollectionUsage> usage = collections.usage();
        return collections.findAll().stream().map(c -> new CollectionView(c, usage.getOrDefault(c.id(),
                new CollectionUsage(0, 0, 0, 0, null)))).toList();
    }

    public List<KnowledgeCollection> enabled() {
        return collections.findAll().stream().filter(KnowledgeCollection::enabled).toList();
    }

    public KnowledgeCollection get(UUID id) {
        return collections.findById(id).orElseThrow(() -> new ResourceNotFoundException("Knowledge collection", id));
    }

    /** The given collection, or the default collection when {@code id} is null. */
    public KnowledgeCollection resolve(UUID id) {
        if (id != null) {
            return get(id);
        }
        return collections.findDefault().or(() -> collections.findAll().stream().findFirst())
                .orElseThrow(() -> new ValidationException("NO_COLLECTION", "Create a knowledge collection first"));
    }

    @Transactional
    public KnowledgeCollection create(String name, String description, boolean enabled) {
        String clean = validateName(name, null);
        return collections.save(KnowledgeCollection.create(clean, validateDescription(description), enabled,
                Times.now(clock)));
    }

    @Transactional
    public KnowledgeCollection update(UUID id, String name, String description, boolean enabled) {
        KnowledgeCollection existing = get(id);
        String clean = validateName(name, id);
        return collections.save(existing.update(clean, validateDescription(description), enabled, Times.now(clock)));
    }

    @Transactional
    public CollectionUsage delete(UUID id) {
        KnowledgeCollection existing = get(id);
        if (existing.defaultCollection()) {
            throw new ConflictException("DEFAULT_COLLECTION", "The default collection cannot be deleted");
        }
        CollectionUsage usage = collections.usage().getOrDefault(id, new CollectionUsage(0, 0, 0, 0, null));
        vectorStore.deleteByCollectionId(id);
        collections.delete(id);
        return usage;
    }

    private String validateName(String name, UUID excludeId) {
        String clean = name == null ? "" : name.replaceAll("\\s+", " ").strip();
        if (clean.isEmpty() || clean.length() > KnowledgeCollection.MAX_NAME_LENGTH) {
            throw new ValidationException("INVALID_COLLECTION_NAME", "The name must have 1 to "
                    + KnowledgeCollection.MAX_NAME_LENGTH + " characters");
        }
        if (collections.existsByName(clean, excludeId)) {
            throw new ConflictException("COLLECTION_EXISTS", "A collection named \"" + clean + "\" already exists");
        }
        return clean;
    }

    private static String validateDescription(String description) {
        if (description != null && description.length() > KnowledgeCollection.MAX_DESCRIPTION_LENGTH) {
            throw new ValidationException("INVALID_DESCRIPTION", "The description must not exceed "
                    + KnowledgeCollection.MAX_DESCRIPTION_LENGTH + " characters");
        }
        return description;
    }

    public record CollectionView(KnowledgeCollection collection, CollectionUsage usage) {
    }
}
