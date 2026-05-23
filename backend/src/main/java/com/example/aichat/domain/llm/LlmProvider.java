package com.example.aichat.domain.llm;

import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound port implemented by every LLM adapter (OpenAI, Qwen, Llama, any OpenAI-compatible server, ...).
 *
 * <p>The application layer only ever talks to this interface using provider-neutral types. Vendor specific
 * payloads, endpoints, authentication and error formats are handled exclusively inside infrastructure adapters.
 *
 * <p>Errors are signalled with subclasses of {@link com.example.aichat.domain.llm.exception.LlmException}.
 */
public interface LlmProvider {

    /** Static description: id, display name, kind, capabilities and configured defaults. */
    ProviderDescriptor descriptor();

    default String providerId() {
        return descriptor().id();
    }

    /** Single-shot completion. Lazy: nothing happens until subscription. */
    Mono<LlmResponse> chat(LlmRequest request);

    /**
     * Token streaming. Emits {@link LlmChunk.TextDelta}/{@link LlmChunk.ReasoningDelta} elements and terminates with
     * exactly one {@link LlmChunk.Completed}. Cancelling the subscription aborts the upstream request.
     */
    Flux<LlmChunk> stream(LlmRequest request);

    /** Models offered by the provider. May perform a (cheap) remote call; callers should cache. */
    List<LlmModel> availableModels();

    /** Cheap health probe. Must never run an inference request. */
    ProviderHealth health();
}
