package com.example.aichat.rag.application;

import com.example.aichat.application.common.ResourceNotFoundException;
import com.example.aichat.application.common.ValidationException;
import com.example.aichat.common.Texts;
import com.example.aichat.rag.application.KnowledgeSourceWriter.IngestionResult;
import com.example.aichat.rag.application.KnowledgeSourceWriter.NewContent;
import com.example.aichat.rag.application.KnowledgeSourceWriter.SourceMetadata;
import com.example.aichat.rag.domain.KnowledgeSource;
import com.example.aichat.rag.domain.KnowledgeSourceRepository;
import com.example.aichat.rag.domain.KnowledgeSourceType;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Manual knowledge entries. The text is stored like an uploaded Markdown file, so it goes through exactly the same
 * pipeline (headings become sections). Editing the text removes the previous chunks and vectors immediately and
 * reindexes; metadata-only edits keep the index.
 */
@Service
public class TextIngestionService {

    public static final String TEXT_MIME_TYPE = "text/markdown";

    private final KnowledgeCollectionService collections;
    private final KnowledgeSourceWriter writer;
    private final KnowledgeSourceRepository sources;
    private final RagProperties properties;

    public TextIngestionService(KnowledgeCollectionService collections, KnowledgeSourceWriter writer,
                                KnowledgeSourceRepository sources, RagProperties properties) {
        this.collections = collections;
        this.writer = writer;
        this.sources = sources;
        this.properties = properties;
    }

    public IngestionResult create(TextCommand command) {
        String text = validateContent(command.content());
        Set<SourceMetadata.Field> provided = EnumSet.of(SourceMetadata.Field.TITLE);
        if (command.collectionId() != null) {
            provided.add(SourceMetadata.Field.COLLECTION);
        }
        if (command.tags() != null) {
            provided.add(SourceMetadata.Field.TAGS);
        }
        if (command.sourceUrl() != null) {
            provided.add(SourceMetadata.Field.SOURCE_URL);
        }
        SourceMetadata metadata = metadata(command.title(), command.collectionId(), command.tags(),
                command.sourceUrl(), provided);
        return writer.ingest(content(text), metadata, command.userId(), command.duplicateStrategy(), null);
    }

    /** Updates a manual entry (all metadata fields are applied); {@code content == null} keeps the current text. */
    public IngestionResult update(UUID id, TextCommand command) {
        KnowledgeSource source = sources.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge source", id));
        if (source.type() != KnowledgeSourceType.TEXT) {
            throw new ValidationException("NOT_A_TEXT_SOURCE", "Only manual text entries can be edited as text");
        }
        SourceMetadata metadata = metadata(command.title(), command.collectionId(), command.tags(),
                command.sourceUrl(), null);
        if (command.content() != null) {
            String text = validateContent(command.content());
            NewContent content = content(text);
            if (!content.checksum().equals(source.checksum())) {
                return writer.replace(source, content, metadata);
            }
        }
        writer.updateMetadata(id, metadata);
        return new IngestionResult(id, source.status(), source.version(), null, false);
    }

    private SourceMetadata metadata(String title, UUID collectionId, List<String> tags, String sourceUrl,
                                    Set<SourceMetadata.Field> provided) {
        if (!Texts.hasText(title)) {
            throw new ValidationException("TITLE_REQUIRED", "A title is required");
        }
        return new SourceMetadata(KnowledgeInputs.title(title, null), collections.resolve(collectionId).id(),
                KnowledgeInputs.tags(tags), KnowledgeInputs.sourceUrl(sourceUrl), provided);
    }

    private String validateContent(String content) {
        if (!Texts.hasText(content)) {
            throw new ValidationException("CONTENT_REQUIRED", "The content must not be empty");
        }
        int max = properties.ingestion().maxTextEntryCharacters();
        if (content.length() > max) {
            throw new ValidationException("CONTENT_TOO_LONG", "The content must not exceed " + max
                    + " characters; upload longer documents as files");
        }
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static NewContent content(String text) {
        return new NewContent(KnowledgeSourceType.TEXT, null, TEXT_MIME_TYPE, text.getBytes(StandardCharsets.UTF_8),
                Checksums.sha256OfText(text));
    }

    public record TextCommand(UUID userId,
                              String title,
                              String content,
                              UUID collectionId,
                              List<String> tags,
                              String sourceUrl,
                              DuplicateStrategy duplicateStrategy) {
    }
}
