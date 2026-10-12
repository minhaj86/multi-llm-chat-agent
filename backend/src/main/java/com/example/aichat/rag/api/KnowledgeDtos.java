package com.example.aichat.rag.api;

import com.example.aichat.rag.application.DuplicateStrategy;
import com.example.aichat.rag.application.KnowledgeBaseService.ContentView;
import com.example.aichat.rag.application.KnowledgeBaseService.DeletionResult;
import com.example.aichat.rag.application.KnowledgeBaseService.SourceDetails;
import com.example.aichat.rag.application.KnowledgeBaseService.SourceSummary;
import com.example.aichat.rag.application.KnowledgeCollectionService.CollectionView;
import com.example.aichat.rag.application.KnowledgeSourceWriter.IngestionResult;
import com.example.aichat.rag.application.RagCitation;
import com.example.aichat.rag.application.RagContextService.RagPreparation;
import com.example.aichat.rag.application.RagTestService.TestAnswer;
import com.example.aichat.rag.application.RetrievalMode;
import com.example.aichat.rag.application.RetrievalOptions;
import com.example.aichat.rag.application.RetrievalResult;
import com.example.aichat.rag.application.RetrievedChunk;
import com.example.aichat.rag.domain.ExtractedDocument.SectionSpan;
import com.example.aichat.rag.domain.IngestionStatus;
import com.example.aichat.rag.domain.KnowledgeChunk;
import com.example.aichat.rag.domain.KnowledgeCollection;
import com.example.aichat.rag.domain.KnowledgeSource;
import com.example.aichat.rag.domain.KnowledgeSourceType;
import com.example.aichat.rag.domain.PageResult;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Request and response bodies of the knowledge-base API. Embeddings are never exposed.
 */
public final class KnowledgeDtos {

    private KnowledgeDtos() {
    }

    // ------------------------------------------------------------------------------------------------ requests

    public record TextRequest(@NotBlank @Size(max = 300) String title,
                              @NotBlank String content,
                              UUID collectionId,
                              @Size(max = 20) List<@Size(max = 50) String> tags,
                              @Size(max = 2000) String sourceUrl,
                              DuplicateStrategy duplicateStrategy) {
    }

    /** Metadata update; {@code content} is only accepted for manual text entries (changing it reindexes). */
    public record UpdateSourceRequest(@NotBlank @Size(max = 300) String title,
                                      UUID collectionId,
                                      @Size(max = 20) List<@Size(max = 50) String> tags,
                                      @Size(max = 2000) String sourceUrl,
                                      String content) {
    }

    public record CollectionRequest(@NotBlank @Size(max = 100) String name,
                                    @Size(max = 500) String description,
                                    Boolean enabled) {
    }

    public record SearchRequest(@NotBlank @Size(max = 4000) String query,
                                @Size(max = 50) Set<UUID> collectionIds,
                                @Min(1) @Max(50) Integer topK,
                                @DecimalMin("-1.0") @DecimalMax("1.0") Double similarityThreshold,
                                @Size(max = 20) Set<String> tags,
                                @Size(max = 50) Set<UUID> sourceIds,
                                RetrievalMode mode) {

        RetrievalOptions options() {
            return new RetrievalOptions(collectionIds, topK, similarityThreshold, tags, sourceIds, mode);
        }
    }

    public record TestAnswerRequest(@NotBlank @Size(max = 4000) String question,
                                    @Size(max = 50) Set<UUID> collectionIds,
                                    @Min(1) @Max(50) Integer topK,
                                    @DecimalMin("-1.0") @DecimalMax("1.0") Double similarityThreshold,
                                    @Size(max = 20) Set<String> tags,
                                    RetrievalMode mode,
                                    @Size(max = 64) String provider,
                                    @Size(max = 200) String model) {

        RetrievalOptions options() {
            return new RetrievalOptions(collectionIds, topK, similarityThreshold, tags, null, mode);
        }
    }

    // ------------------------------------------------------------------------------------------------ responses

    public record PageResponse<T>(List<T> items, int page, int size, long total, int totalPages) {

        static <S, T> PageResponse<T> of(PageResult<S> page, java.util.function.Function<S, T> mapper) {
            return new PageResponse<>(page.items().stream().map(mapper).toList(), page.page(), page.size(),
                    page.total(), page.totalPages());
        }
    }

    public record IngestionResponse(UUID sourceId, IngestionStatus status, int version, UUID relatedSourceId,
                                    boolean replaced) {

        static IngestionResponse of(IngestionResult result) {
            return new IngestionResponse(result.sourceId(), result.status(), result.version(),
                    result.relatedSourceId(), result.replaced());
        }
    }

    public record SourceResponse(UUID id,
                                 KnowledgeSourceType type,
                                 String typeLabel,
                                 String title,
                                 String originalFilename,
                                 String mimeType,
                                 String sourceUrl,
                                 UUID collectionId,
                                 String collectionName,
                                 IngestionStatus status,
                                 boolean enabled,
                                 List<String> tags,
                                 String checksum,
                                 long fileSize,
                                 Integer characterCount,
                                 int version,
                                 UUID parentSourceId,
                                 UUID createdBy,
                                 Instant createdAt,
                                 Instant updatedAt,
                                 Instant indexedAt,
                                 String embeddingProvider,
                                 String embeddingModel,
                                 int chunkCount,
                                 String errorMessage,
                                 boolean stale) {

        static SourceResponse of(SourceSummary summary) {
            return of(summary.source(), summary.collectionName(), summary.typeLabel(), summary.stale());
        }

        static SourceResponse of(KnowledgeSource s, String collectionName, String typeLabel, boolean stale) {
            return new SourceResponse(s.id(), s.type(), typeLabel, s.title(), s.originalFilename(), s.mimeType(),
                    s.sourceUrl(), s.collectionId(), collectionName, s.status(), s.enabled(), s.tags(), s.checksum(),
                    s.fileSize(), s.characterCount(), s.version(), s.parentSourceId(), s.createdBy(), s.createdAt(),
                    s.updatedAt(), s.indexedAt(), s.embeddingProvider(), s.embeddingModel(), s.chunkCount(),
                    s.errorMessage(), stale);
        }
    }

    public record SourceDetailsResponse(SourceResponse source,
                                        String createdByName,
                                        long vectorCount,
                                        Integer estimatedTokens,
                                        boolean workerActive,
                                        String vectorStore,
                                        Map<String, Object> ingestionStats) {

        static SourceDetailsResponse of(SourceDetails d) {
            return new SourceDetailsResponse(SourceResponse.of(d.source(), d.collectionName(), d.typeLabel(),
                    d.stale()), d.createdByName(), d.vectorCount(), d.estimatedTokens(), d.workerActive(),
                    d.vectorStore(), d.source().ingestionStats());
        }
    }

    public record StatusResponse(UUID id, IngestionStatus status, boolean enabled, int chunkCount,
                                 String errorMessage, Instant indexedAt, Instant updatedAt) {

        static StatusResponse of(KnowledgeSource s) {
            return new StatusResponse(s.id(), s.status(), s.enabled(), s.chunkCount(), s.errorMessage(),
                    s.indexedAt(), s.updatedAt());
        }
    }

    public record ChunkResponse(UUID id,
                                UUID sourceId,
                                int chunkIndex,
                                String content,
                                int characterCount,
                                int tokenCount,
                                int characterStart,
                                int characterEnd,
                                Integer pageNumber,
                                String sectionTitle,
                                Map<String, Object> metadata,
                                Instant createdAt) {

        static ChunkResponse of(KnowledgeChunk c) {
            return new ChunkResponse(c.id(), c.sourceId(), c.chunkIndex(), c.content(), c.content().length(),
                    c.tokenCount(), c.characterStart(), c.characterEnd(), c.pageNumber(), c.sectionTitle(),
                    c.metadata(), c.createdAt());
        }
    }

    public record ContentResponse(UUID sourceId,
                                  boolean available,
                                  String text,
                                  int characterCount,
                                  int estimatedTokens,
                                  boolean truncated,
                                  int pageCount,
                                  List<SectionSpan> sections,
                                  Map<String, String> properties,
                                  Instant extractedAt) {

        static ContentResponse of(ContentView v) {
            return new ContentResponse(v.sourceId(), v.available(), v.text(), v.characterCount(), v.estimatedTokens(),
                    v.truncated(), v.pageCount(), v.sections(), v.properties(), v.extractedAt());
        }
    }

    public record DeletionResponse(UUID sourceId, String title, long deletedChunks, long deletedEmbeddings) {

        static DeletionResponse of(DeletionResult r) {
            return new DeletionResponse(r.sourceId(), r.title(), r.chunks(), r.vectors());
        }
    }

    public record CollectionResponse(UUID id,
                                     String name,
                                     String description,
                                     boolean enabled,
                                     boolean defaultCollection,
                                     long sources,
                                     long enabledSources,
                                     long chunks,
                                     long bytes,
                                     Instant createdAt,
                                     Instant updatedAt) {

        static CollectionResponse of(CollectionView view) {
            KnowledgeCollection c = view.collection();
            return new CollectionResponse(c.id(), c.name(), c.description(), c.enabled(), c.defaultCollection(),
                    view.usage().sources(), view.usage().enabledSources(), view.usage().chunks(),
                    view.usage().bytes(), c.createdAt(), c.updatedAt());
        }
    }

    public record CollectionDeletionResponse(UUID id, long deletedSources, long deletedChunks) {
    }

    /** What regular users see of a collection (for the chat knowledge selector). */
    public record UserCollectionResponse(UUID id, String name, String description) {
    }

    public record UserKnowledgeResponse(boolean enabled, List<UserCollectionResponse> collections) {
    }

    /** A cited chunk as shown to users who click a citation. */
    public record CitationChunkResponse(UUID chunkId,
                                        UUID sourceId,
                                        String title,
                                        String collectionName,
                                        Integer pageNumber,
                                        String sectionTitle,
                                        int chunkIndex,
                                        String content,
                                        String sourceUrl) {
    }

    public record RetrievedChunkResponse(int rank,
                                         UUID chunkId,
                                         UUID sourceId,
                                         String sourceTitle,
                                         KnowledgeSourceType sourceType,
                                         UUID collectionId,
                                         String collectionName,
                                         int chunkIndex,
                                         Integer pageNumber,
                                         String sectionTitle,
                                         Double similarity,
                                         Double keywordScore,
                                         Double rerankScore,
                                         double score,
                                         int tokenCount,
                                         String content) {

        static RetrievedChunkResponse of(int rank, RetrievedChunk c) {
            return new RetrievedChunkResponse(rank, c.chunkId(), c.sourceId(), c.sourceTitle(), c.sourceType(),
                    c.collectionId(), c.collectionName(), c.chunkIndex(), c.pageNumber(), c.sectionTitle(),
                    c.similarity(), c.keywordScore(), c.rerankScore(), c.score(), c.tokenCount(), c.content());
        }
    }

    public record SearchResponse(String query,
                                 List<RetrievedChunkResponse> results,
                                 int candidates,
                                 Set<UUID> collectionIds,
                                 RetrievalMode mode,
                                 int topK,
                                 double similarityThreshold,
                                 String embeddingProvider,
                                 String embeddingModel,
                                 String reranker,
                                 long embeddingMs,
                                 long searchMs,
                                 long totalMs) {

        static SearchResponse of(RetrievalResult r) {
            List<RetrievedChunkResponse> results = new java.util.ArrayList<>();
            for (int i = 0; i < r.chunks().size(); i++) {
                results.add(RetrievedChunkResponse.of(i + 1, r.chunks().get(i)));
            }
            return new SearchResponse(r.query(), results, r.candidates(), r.collectionIds(), r.mode(), r.topK(),
                    r.similarityThreshold(), r.embeddingProvider(), r.embeddingModel(), r.reranker(), r.embeddingMs(),
                    r.searchMs(), r.totalMs());
        }
    }

    public record TestAnswerResponse(String answer,
                                     String reasoning,
                                     String provider,
                                     String providerName,
                                     String model,
                                     boolean local,
                                     long latencyMs,
                                     Integer inputTokens,
                                     Integer outputTokens,
                                     BigDecimal estimatedCost,
                                     int contextTokens,
                                     int contextChunks,
                                     boolean contextTruncated,
                                     List<RagCitation> citations,
                                     SearchResponse retrieval,
                                     String systemMessage,
                                     String userMessage) {

        static TestAnswerResponse of(TestAnswer a) {
            RagPreparation p = a.preparation();
            return new TestAnswerResponse(a.answer(), a.reasoning(), a.provider(), a.providerName(), a.model(),
                    a.local(), a.latencyMs(), a.inputTokens(), a.outputTokens(), a.estimatedCost(),
                    p.context().tokens(), p.context().includedChunks().size(), p.context().truncated(),
                    p.context().citations(), SearchResponse.of(p.retrieval()), a.systemMessage(), a.userMessage());
        }
    }
}
