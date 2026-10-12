package com.example.aichat.rag.domain;

import java.util.List;

/**
 * Port for text embedding models. Deliberately independent of the chat {@code LlmProvider}s: the knowledge base
 * may use OpenAI embeddings while chatting with a local Qwen model, or vice versa.
 */
public interface EmbeddingProvider {

    /** Provider identifier, e.g. {@code openai}, {@code ollama}, {@code hashing}. */
    String providerId();

    /** Model identifier stored with every vector; vectors of different models are never compared. */
    String modelId();

    /** Vector length, or {@code 0} until the first embedding has been produced (some servers do not announce it). */
    int dimensions();

    /** One embedding per input text, in the same order. Implementations batch as appropriate for the model. */
    List<float[]> embed(List<String> texts);
}
