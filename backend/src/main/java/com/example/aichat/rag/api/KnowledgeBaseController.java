package com.example.aichat.rag.api;

import com.example.aichat.application.auth.CurrentUser;
import com.example.aichat.application.common.ResourceNotFoundException;
import com.example.aichat.rag.api.KnowledgeDtos.CitationChunkResponse;
import com.example.aichat.rag.api.KnowledgeDtos.UserCollectionResponse;
import com.example.aichat.rag.api.KnowledgeDtos.UserKnowledgeResponse;
import com.example.aichat.rag.application.KnowledgeAccessPolicy;
import com.example.aichat.rag.application.KnowledgeCollectionService;
import com.example.aichat.rag.application.RagProperties;
import com.example.aichat.rag.domain.KnowledgeChunk;
import com.example.aichat.rag.domain.KnowledgeChunkRepository;
import com.example.aichat.rag.domain.KnowledgeCollection;
import com.example.aichat.rag.domain.KnowledgeSource;
import com.example.aichat.rag.domain.KnowledgeSourceRepository;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Knowledge-base endpoints for every signed-in user: the collections available for chat and the chunk behind a
 * citation. Only enabled, indexed sources of enabled collections that the {@link KnowledgeAccessPolicy} lets the user
 * read are visible; everything else is "not found".
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeBaseController {

    private final KnowledgeCollectionService collections;
    private final KnowledgeChunkRepository chunks;
    private final KnowledgeSourceRepository sources;
    private final KnowledgeAccessPolicy accessPolicy;
    private final RagProperties properties;

    public KnowledgeBaseController(KnowledgeCollectionService collections, KnowledgeChunkRepository chunks,
                                   KnowledgeSourceRepository sources, KnowledgeAccessPolicy accessPolicy,
                                   RagProperties properties) {
        this.collections = collections;
        this.chunks = chunks;
        this.sources = sources;
        this.accessPolicy = accessPolicy;
        this.properties = properties;
    }

    @GetMapping("/collections")
    public UserKnowledgeResponse collections(@AuthenticationPrincipal CurrentUser user) {
        return new UserKnowledgeResponse(properties.enabled(), collections.enabled().stream()
                .filter(c -> accessPolicy.canRead(user.id(), c))
                .map(c -> new UserCollectionResponse(c.id(), c.name(), c.description())).toList());
    }

    @GetMapping("/chunks/{chunkId}")
    public CitationChunkResponse chunk(@AuthenticationPrincipal CurrentUser user,
                                       @PathVariable("chunkId") UUID chunkId) {
        KnowledgeChunk chunk = chunks.findById(chunkId)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge chunk", chunkId));
        KnowledgeSource source = sources.findById(chunk.sourceId())
                .filter(KnowledgeSource::isSearchable)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge chunk", chunkId));
        Map<UUID, KnowledgeCollection> enabled = collections.enabled().stream()
                .collect(Collectors.toMap(KnowledgeCollection::id, Function.identity()));
        KnowledgeCollection collection = enabled.get(chunk.collectionId());
        if (collection == null || !accessPolicy.canRead(user.id(), collection)) {
            throw new ResourceNotFoundException("Knowledge chunk", chunkId);
        }
        return new CitationChunkResponse(chunk.id(), source.id(), source.title(), collection.name(),
                chunk.pageNumber(), chunk.sectionTitle(), chunk.chunkIndex(), chunk.content(), source.sourceUrl());
    }
}
