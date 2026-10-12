package com.example.aichat.application.chat.knowledge;

import java.util.List;

/**
 * Result of a knowledge lookup, ready to be merged into an LLM request.
 *
 * @param requested      knowledge was requested for this answer (shown to the user even when nothing was found)
 * @param instructions   trusted rules appended to the system message (how to use and cite the excerpts)
 * @param knowledgeBlock untrusted excerpts, delimited, placed in the user message before the question
 * @param tokens         estimated tokens of instructions plus knowledge block
 * @param retrievedCount chunks that matched before the context budget was applied
 * @param notice         message for the user when the knowledge base could not help (no match, unavailable)
 */
public record KnowledgeContext(boolean requested,
                               String instructions,
                               String knowledgeBlock,
                               List<KnowledgeCitation> citations,
                               int tokens,
                               int retrievedCount,
                               String notice) {

    public static final KnowledgeContext NONE = new KnowledgeContext(false, null, null, List.of(), 0, 0, null);

    public KnowledgeContext {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    public static KnowledgeContext withoutKnowledge(int retrievedCount, String notice) {
        return new KnowledgeContext(true, null, null, List.of(), 0, retrievedCount, notice);
    }

    public boolean hasKnowledge() {
        return knowledgeBlock != null && !knowledgeBlock.isBlank();
    }

    /** The user turn sent to the model: excerpts first, then the original question. */
    public String augment(String question) {
        return hasKnowledge() ? knowledgeBlock + "\n\nQuestion:\n" + question : question;
    }
}
