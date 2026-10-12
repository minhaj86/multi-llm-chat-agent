package com.example.aichat.rag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aichat.application.chat.knowledge.KnowledgeContext;
import com.example.aichat.application.chat.knowledge.KnowledgeQuery;
import com.example.aichat.rag.application.RagContextService.RagPreparation;
import com.example.aichat.rag.domain.KnowledgeCollection;
import com.example.aichat.rag.domain.RagExecutionRepository;
import com.example.aichat.support.RagTestProperties;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RagChatAugmenterTest {

    private static final UUID USER = UUID.randomUUID();
    private final KnowledgeCollection engineering = collection("Engineering");
    private final KnowledgeCollection finance = collection("Finance");
    private final RagContextService contextService = mock(RagContextService.class);
    private final KnowledgeCollectionService collections = mock(KnowledgeCollectionService.class);
    private final KnowledgeAccessPolicy policy = (userId, collection) -> !collection.equals(finance);
    private final RagChatAugmenter augmenter = new RagChatAugmenter(contextService, mock(RagExecutionRecorder.class),
            mock(RagExecutionRepository.class), collections, policy, RagTestProperties.defaults());

    @Test
    void searchesOnlyTheCollectionsTheUserMayRead() {
        when(collections.enabled()).thenReturn(List.of(engineering, finance));
        when(contextService.prepare(anyString(), any())).thenReturn(emptyPreparation());

        augmenter.augment(query(Set.of()));

        ArgumentCaptor<RetrievalOptions> options = ArgumentCaptor.forClass(RetrievalOptions.class);
        verify(contextService).prepare(eq("How are prices converted?"), options.capture());
        assertThat(options.getValue().collectionIds()).containsExactly(engineering.id());
    }

    @Test
    void answersWithoutKnowledgeWhenNoSelectedCollectionIsReadable() {
        when(collections.enabled()).thenReturn(List.of(engineering, finance));

        KnowledgeContext context = augmenter.augment(query(Set.of(finance.id())));

        assertThat(context.hasKnowledge()).isFalse();
        assertThat(context.notice()).contains("None of the selected knowledge collections is available to you");
        verify(contextService, never()).prepare(anyString(), any());
    }

    private static KnowledgeQuery query(Set<UUID> collectionIds) {
        return new KnowledgeQuery(USER, UUID.randomUUID(), UUID.randomUUID(), "How are prices converted?",
                collectionIds, "corr-1");
    }

    private static RagPreparation emptyPreparation() {
        RetrievalResult retrieval = new RetrievalResult("q", List.of(), 0, Set.of(), RetrievalMode.VECTOR, 8, 0.7,
                "fake", "fake-256", "none", 0, 0, 0);
        return new RagPreparation(retrieval, new RagContext(null, null, List.of(), List.of(), 0, false));
    }

    private static KnowledgeCollection collection(String name) {
        Instant now = Instant.parse("2026-10-12T10:00:00Z");
        return new KnowledgeCollection(UUID.randomUUID(), name, null, true, false, now, now);
    }
}
