package com.example.aichat.rag.application;

import java.util.List;

/**
 * Knowledge prepared for an LLM request.
 *
 * @param instructions   trusted rules for the system message ({@code null} when there is no knowledge)
 * @param knowledgeBlock delimited, untrusted excerpts placed in the user turn ({@code null} when empty)
 * @param includedChunks chunks that fit the budget, in citation order
 * @param tokens         estimated tokens of instructions plus excerpts
 * @param truncated      some retrieved chunks were left out because of the token or chunk budget
 */
public record RagContext(String instructions,
                         String knowledgeBlock,
                         List<RagCitation> citations,
                         List<RetrievedChunk> includedChunks,
                         int tokens,
                         boolean truncated) {

    public RagContext {
        citations = List.copyOf(citations);
        includedChunks = List.copyOf(includedChunks);
    }

    public boolean isEmpty() {
        return knowledgeBlock == null;
    }

    /** User turn for the model: the excerpts, then the question. */
    public String userMessage(String question) {
        return isEmpty() ? question : knowledgeBlock + "\n\nQuestion:\n" + question;
    }
}
