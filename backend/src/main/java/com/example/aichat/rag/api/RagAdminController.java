package com.example.aichat.rag.api;

import com.example.aichat.application.auth.CurrentUser;
import com.example.aichat.application.common.ResourceNotFoundException;
import com.example.aichat.application.common.ValidationException;
import com.example.aichat.rag.api.KnowledgeDtos.ChunkResponse;
import com.example.aichat.rag.api.KnowledgeDtos.CollectionDeletionResponse;
import com.example.aichat.rag.api.KnowledgeDtos.CollectionRequest;
import com.example.aichat.rag.api.KnowledgeDtos.CollectionResponse;
import com.example.aichat.rag.api.KnowledgeDtos.ContentResponse;
import com.example.aichat.rag.api.KnowledgeDtos.DeletionResponse;
import com.example.aichat.rag.api.KnowledgeDtos.IngestionResponse;
import com.example.aichat.rag.api.KnowledgeDtos.PageResponse;
import com.example.aichat.rag.api.KnowledgeDtos.SourceDetailsResponse;
import com.example.aichat.rag.api.KnowledgeDtos.SourceResponse;
import com.example.aichat.rag.api.KnowledgeDtos.StatusResponse;
import com.example.aichat.rag.api.KnowledgeDtos.TextRequest;
import com.example.aichat.rag.api.KnowledgeDtos.UpdateSourceRequest;
import com.example.aichat.rag.application.DocumentIngestionService;
import com.example.aichat.rag.application.DocumentIngestionService.UploadCommand;
import com.example.aichat.rag.application.DuplicateStrategy;
import com.example.aichat.rag.application.KnowledgeBaseService;
import com.example.aichat.rag.application.KnowledgeBaseService.KnowledgeStats;
import com.example.aichat.rag.application.KnowledgeCollectionService;
import com.example.aichat.rag.application.TextIngestionService;
import com.example.aichat.rag.application.TextIngestionService.TextCommand;
import com.example.aichat.rag.domain.IngestionStatus;
import com.example.aichat.rag.domain.KnowledgeCollectionRepository.CollectionUsage;
import com.example.aichat.rag.domain.KnowledgeSourceQuery;
import com.example.aichat.rag.domain.KnowledgeSourceType;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Knowledge-base administration ({@code /api/admin/**} requires the ADMIN role). Uploads return {@code 202} with
 * status {@code UPLOADED}; indexing continues in the background and can be followed through {@code /status}.
 */
@RestController
@RequestMapping("/api/admin/knowledge")
public class RagAdminController {

    private final DocumentIngestionService documents;
    private final TextIngestionService texts;
    private final KnowledgeBaseService knowledgeBase;
    private final KnowledgeCollectionService collections;

    public RagAdminController(DocumentIngestionService documents, TextIngestionService texts,
                              KnowledgeBaseService knowledgeBase, KnowledgeCollectionService collections) {
        this.documents = documents;
        this.texts = texts;
        this.knowledgeBase = knowledgeBase;
        this.collections = collections;
    }

    @PostMapping(path = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IngestionResponse upload(@AuthenticationPrincipal CurrentUser user,
                                    @RequestPart("file") MultipartFile file,
                                    @RequestParam(name = "title", required = false) String title,
                                    @RequestParam(name = "collectionId", required = false) UUID collectionId,
                                    @RequestParam(name = "tags", required = false) List<String> tags,
                                    @RequestParam(name = "sourceUrl", required = false) String sourceUrl,
                                    @RequestParam(name = "duplicateStrategy", required = false)
                                    DuplicateStrategy duplicateStrategy,
                                    @RequestParam(name = "replaceSourceId", required = false) UUID replaceSourceId)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("EMPTY_FILE", "The file is empty");
        }
        return IngestionResponse.of(documents.upload(new UploadCommand(user.id(), file.getOriginalFilename(),
                file.getBytes(), title, collectionId, splitTags(tags), sourceUrl, duplicateStrategy,
                replaceSourceId)));
    }

    @PostMapping(path = "/text", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IngestionResponse addText(@AuthenticationPrincipal CurrentUser user,
                                     @Valid @RequestBody TextRequest request) {
        return IngestionResponse.of(texts.create(new TextCommand(user.id(), request.title(), request.content(),
                request.collectionId(), request.tags(), request.sourceUrl(), request.duplicateStrategy())));
    }

    @GetMapping
    public PageResponse<SourceResponse> list(@RequestParam(name = "q", required = false) String text,
                                             @RequestParam(name = "collectionId", required = false) UUID collectionId,
                                             @RequestParam(name = "type", required = false) KnowledgeSourceType type,
                                             @RequestParam(name = "status", required = false) IngestionStatus status,
                                             @RequestParam(name = "enabled", required = false) Boolean enabled,
                                             @RequestParam(name = "tag", required = false) String tag,
                                             @RequestParam(name = "createdFrom", required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
                                             @RequestParam(name = "createdTo", required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
                                             @RequestParam(name = "sort", defaultValue = "CREATED_AT")
                                             KnowledgeSourceQuery.Sort sort,
                                             @RequestParam(name = "direction", defaultValue = "desc") String direction,
                                             @RequestParam(name = "page", defaultValue = "0") int page,
                                             @RequestParam(name = "size", defaultValue = "25") int size) {
        KnowledgeSourceQuery query = new KnowledgeSourceQuery(text, collectionId, type, status, enabled, tag,
                createdFrom == null ? null : createdFrom.atStartOfDay(ZoneOffset.UTC).toInstant(),
                createdTo == null ? null : createdTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                sort, "asc".equalsIgnoreCase(direction), page, size);
        return PageResponse.of(knowledgeBase.list(query), SourceResponse::of);
    }

    @GetMapping("/stats")
    public KnowledgeStats stats() {
        return knowledgeBase.stats();
    }

    @PostMapping("/reindex-stale")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Integer> reindexStale() {
        return Map.of("scheduled", knowledgeBase.reindexStale());
    }

    @GetMapping("/{id}")
    public SourceDetailsResponse get(@PathVariable("id") UUID id) {
        return SourceDetailsResponse.of(knowledgeBase.get(id));
    }

    @GetMapping("/{id}/status")
    public StatusResponse status(@PathVariable("id") UUID id) {
        return StatusResponse.of(knowledgeBase.get(id).source());
    }

    @PutMapping("/{id}")
    public SourceDetailsResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable("id") UUID id,
                                        @Valid @RequestBody UpdateSourceRequest request) {
        KnowledgeBaseService.SourceDetails current = knowledgeBase.get(id);
        if (current.source().type() == KnowledgeSourceType.TEXT) {
            texts.update(id, new TextCommand(user.id(), request.title(), request.content(), request.collectionId(),
                    request.tags(), request.sourceUrl(), null));
        } else {
            if (request.content() != null) {
                throw new ValidationException("CONTENT_NOT_EDITABLE",
                        "The text of uploaded documents cannot be edited; upload a new version instead");
            }
            knowledgeBase.updateMetadata(id, request.title(), request.collectionId(), request.tags(),
                    request.sourceUrl());
        }
        return SourceDetailsResponse.of(knowledgeBase.get(id));
    }

    @DeleteMapping("/{id}")
    public DeletionResponse delete(@PathVariable("id") UUID id) {
        return DeletionResponse.of(knowledgeBase.delete(id));
    }

    @PostMapping("/{id}/reindex")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StatusResponse reindex(@PathVariable("id") UUID id,
                                  @RequestParam(name = "reextract", defaultValue = "false") boolean reextract) {
        knowledgeBase.reindex(id, reextract);
        return StatusResponse.of(knowledgeBase.get(id).source());
    }

    @PostMapping("/{id}/enable")
    public StatusResponse enable(@PathVariable("id") UUID id) {
        knowledgeBase.setEnabled(id, true);
        return StatusResponse.of(knowledgeBase.get(id).source());
    }

    @PostMapping("/{id}/disable")
    public StatusResponse disable(@PathVariable("id") UUID id) {
        knowledgeBase.setEnabled(id, false);
        return StatusResponse.of(knowledgeBase.get(id).source());
    }

    @GetMapping("/{id}/chunks")
    public PageResponse<ChunkResponse> chunks(@PathVariable("id") UUID id,
                                              @RequestParam(name = "page", defaultValue = "0") int page,
                                              @RequestParam(name = "size", defaultValue = "50") int size) {
        return PageResponse.of(knowledgeBase.chunks(id, page, size), ChunkResponse::of);
    }

    @GetMapping("/{id}/content")
    public ContentResponse content(@PathVariable("id") UUID id,
                                   @RequestParam(name = "maxCharacters", required = false) Integer maxCharacters) {
        return ContentResponse.of(knowledgeBase.content(id, maxCharacters));
    }

    @GetMapping("/collections")
    public List<CollectionResponse> collections() {
        return collections.list().stream().map(CollectionResponse::of).toList();
    }

    @PostMapping("/collections")
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionResponse createCollection(@Valid @RequestBody CollectionRequest request) {
        UUID id = collections.create(request.name(), request.description(),
                request.enabled() == null || request.enabled()).id();
        return collection(id);
    }

    @PutMapping("/collections/{id}")
    public CollectionResponse updateCollection(@PathVariable("id") UUID id,
                                               @Valid @RequestBody CollectionRequest request) {
        collections.update(id, request.name(), request.description(), request.enabled() == null || request.enabled());
        return collection(id);
    }

    @DeleteMapping("/collections/{id}")
    public CollectionDeletionResponse deleteCollection(@PathVariable("id") UUID id) {
        CollectionUsage usage = collections.delete(id);
        return new CollectionDeletionResponse(id, usage.sources(), usage.chunks());
    }

    private CollectionResponse collection(UUID id) {
        return collections.list().stream().filter(view -> view.collection().id().equals(id)).findFirst()
                .map(CollectionResponse::of)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge collection", id));
    }

    /** Accepts repeated parameters and comma-separated values ({@code tags=a&tags=b} or {@code tags=a,b}). */
    /** Accepts repeated and comma-separated values; {@code null} (not sent) is kept to tell "absent" from "empty". */
    private static List<String> splitTags(List<String> tags) {
        if (tags == null) {
            return null;
        }
        return tags.stream().flatMap(t -> Arrays.stream(t.split(","))).map(String::strip)
                .filter(t -> !t.isEmpty()).toList();
    }
}
