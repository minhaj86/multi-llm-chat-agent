package com.example.aichat.rag.application;

import com.example.aichat.common.Texts;
import com.example.aichat.rag.application.KnowledgeSourceWriter.IngestionResult;
import com.example.aichat.rag.application.KnowledgeSourceWriter.NewContent;
import com.example.aichat.rag.application.KnowledgeSourceWriter.SourceMetadata;
import com.example.aichat.rag.domain.KnowledgeSourceType;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * File uploads: validation (name, size, type, real content), SHA-256 duplicate detection, storage of the original
 * and asynchronous indexing. The request returns as soon as the file is stored (status {@code UPLOADED}).
 */
@Service
public class DocumentIngestionService {

    private final UploadValidator validator;
    private final KnowledgeCollectionService collections;
    private final KnowledgeSourceWriter writer;

    public DocumentIngestionService(UploadValidator validator, KnowledgeCollectionService collections,
                                    KnowledgeSourceWriter writer) {
        this.validator = validator;
        this.collections = collections;
        this.writer = writer;
    }

    public IngestionResult upload(UploadCommand command) {
        UploadValidator.UploadedFile file = validator.validate(command.filename(), command.content());
        Set<SourceMetadata.Field> provided = EnumSet.noneOf(SourceMetadata.Field.class);
        if (Texts.hasText(command.title())) {
            provided.add(SourceMetadata.Field.TITLE);
        }
        if (command.collectionId() != null) {
            provided.add(SourceMetadata.Field.COLLECTION);
        }
        if (command.tags() != null) {
            provided.add(SourceMetadata.Field.TAGS);
        }
        if (command.sourceUrl() != null) {
            provided.add(SourceMetadata.Field.SOURCE_URL);
        }
        SourceMetadata metadata = new SourceMetadata(KnowledgeInputs.title(command.title(), file.filename()),
                collections.resolve(command.collectionId()).id(), KnowledgeInputs.tags(command.tags()),
                KnowledgeInputs.sourceUrl(command.sourceUrl()), provided);
        NewContent content = new NewContent(KnowledgeSourceType.FILE, file.filename(), file.type().mimeType(),
                file.content(), file.checksum());
        return writer.ingest(content, metadata, command.userId(), command.duplicateStrategy(),
                command.replaceSourceId());
    }

    /**
     * @param tags            {@code null} when not sent: a replaced source keeps its tags (likewise for a null
     *                        title, collection or source URL; an empty URL clears it)
     * @param replaceSourceId replace the content of this source instead of creating a new one
     */
    public record UploadCommand(UUID userId,
                                String filename,
                                byte[] content,
                                String title,
                                UUID collectionId,
                                List<String> tags,
                                String sourceUrl,
                                DuplicateStrategy duplicateStrategy,
                                UUID replaceSourceId) {
    }
}
