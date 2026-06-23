package com.example.aichat.application.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.aichat.domain.llm.LlmChunk;
import com.example.aichat.domain.llm.LlmMessage;
import com.example.aichat.domain.llm.LlmModel;
import com.example.aichat.domain.llm.LlmProvider;
import com.example.aichat.domain.llm.LlmRequest;
import com.example.aichat.domain.llm.LlmResponse;
import com.example.aichat.domain.llm.ProviderDefaults;
import com.example.aichat.domain.llm.ProviderDescriptor;
import com.example.aichat.domain.llm.ProviderHealth;
import com.example.aichat.domain.llm.ProviderKind;
import com.example.aichat.support.InMemoryProviderSettingsRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class LlmProviderRegistryTest {

    private ProviderSettingsService settings;
    private LlmProviderRegistry registry;

    @BeforeEach
    void setUp() {
        settings = new ProviderSettingsService(new InMemoryProviderSettingsRepository(), Clock.systemUTC(), Duration.ZERO);
        registry = new LlmProviderRegistry(List.of(
                provider("openai", "OpenAI", ProviderKind.CLOUD, "gpt-5-mini"),
                provider("qwen", "Qwen Local", ProviderKind.LOCAL, "qwen3:8b"),
                provider("llama", "Llama Local", ProviderKind.LOCAL, "llama3.1:8b")), "openai", settings);
    }

    @Test
    void resolvesTheRequestedProviderAndItsDefaultModel() {
        ResolvedProvider resolved = registry.resolve("qwen", null);

        assertThat(resolved.providerId()).isEqualTo("qwen");
        assertThat(resolved.model()).isEqualTo("qwen3:8b");
        assertThat(resolved.isLocal()).isTrue();
    }

    @Test
    void explicitModelWins() {
        assertThat(registry.resolve("llama", "llama3.2:3b").model()).isEqualTo("llama3.2:3b");
    }

    @Test
    void blankProviderFallsBackToTheConfiguredDefault() {
        ResolvedProvider resolved = registry.resolve(" ", null);

        assertThat(resolved.providerId()).isEqualTo("openai");
        assertThat(resolved.model()).isEqualTo("gpt-5-mini");
    }

    @Test
    void unknownProviderIsRejected() {
        assertThatThrownBy(() -> registry.resolve("anthropic", null))
                .isInstanceOf(UnknownProviderException.class)
                .hasMessageContaining("anthropic");
    }

    @Test
    void providerDisabledAtRuntimeIsRejectedWithoutRestart() {
        assertThat(registry.resolve("llama", null).providerId()).isEqualTo("llama");
        settings.update(registry.require("llama").descriptor(),
                new ProviderSettingsUpdate(false, null, null, null, null), "admin");

        assertThatThrownBy(() -> registry.resolve("llama", null)).isInstanceOf(ProviderDisabledException.class);
        assertThat(registry.isEnabled("qwen")).isTrue();
    }

    @Test
    void administratorOverridesApplyToResolvedRequests() {
        settings.update(registry.require("qwen").descriptor(),
                new ProviderSettingsUpdate(null, "qwen3:32b", 42, 0.2, 512), "admin");

        ResolvedProvider resolved = registry.resolve("qwen", null);
        LlmRequest request = resolved.request(List.of(LlmMessage.user("hi")), "cid");

        assertThat(resolved.model()).isEqualTo("qwen3:32b");
        assertThat(request.timeout()).isEqualTo(Duration.ofSeconds(42));
        assertThat(request.options().temperature()).isEqualTo(0.2);
        assertThat(request.options().maxOutputTokens()).isEqualTo(512);
        assertThat(request.correlationId()).contains("cid");
    }

    @Test
    void resetRestoresConfiguredDefaults() {
        ProviderDescriptor qwen = registry.require("qwen").descriptor();
        settings.update(qwen, new ProviderSettingsUpdate(false, "other", null, null, null), "admin");
        settings.reset(qwen);

        assertThat(registry.resolve("qwen", null).model()).isEqualTo("qwen3:8b");
    }

    @Test
    void duplicateIdsAndUnknownDefaultProviderFailFast() {
        LlmProvider a = provider("x", "X", ProviderKind.LOCAL, "m");
        assertThatThrownBy(() -> new LlmProviderRegistry(List.of(a, a), null, settings))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LlmProviderRegistry(List.of(a), "missing", settings))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void providersAreListedInConfigurationOrder() {
        assertThat(registry.all()).extracting(LlmProvider::providerId).containsExactly("openai", "qwen", "llama");
    }

    static LlmProvider provider(String id, String name, ProviderKind kind, String defaultModel) {
        ProviderDescriptor descriptor = new ProviderDescriptor(id, name, "test", kind, Set.of(), "http://test", null,
                new ProviderDefaults(true, defaultModel, Duration.ofSeconds(30), 0.7, 1024));
        return new LlmProvider() {
            @Override
            public ProviderDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public Mono<LlmResponse> chat(LlmRequest request) {
                return Mono.empty();
            }

            @Override
            public Flux<LlmChunk> stream(LlmRequest request) {
                return Flux.empty();
            }

            @Override
            public List<LlmModel> availableModels() {
                return List.of(LlmModel.of(defaultModel));
            }

            @Override
            public ProviderHealth health() {
                return ProviderHealth.unknown(id);
            }
        };
    }
}
