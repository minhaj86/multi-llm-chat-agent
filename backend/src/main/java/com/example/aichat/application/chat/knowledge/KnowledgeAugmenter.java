package com.example.aichat.application.chat.knowledge;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port through which chat obtains knowledge-base context. The chat module only knows this interface; retrieval,
 * embeddings and vector storage live in the RAG module ({@code com.example.aichat.rag}), which implements it.
 * Without an implementation, chat works exactly as before.
 */
public interface KnowledgeAugmenter {

    /** Whether retrieval is available at all ({@code rag.enabled}). */
    boolean isEnabled();

    /**
     * Retrieves knowledge for the question and prepares it for the LLM request. Never throws: when the knowledge
     * base cannot be searched, a context with a notice and no knowledge is returned so the chat can continue.
     */
    KnowledgeContext augment(KnowledgeQuery query);

    /** Citations of previously generated answers, by assistant message id. */
    Map<UUID, List<KnowledgeCitation>> citations(Collection<UUID> assistantMessageIds);
}
