package com.example.aichat.domain.llm;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Static description of a configured provider instance.
 *
 * @param id             stable identifier used in APIs (e.g. {@code openai}, {@code qwen})
 * @param displayName    human readable name (e.g. "Qwen Local")
 * @param type           adapter type that created the provider (e.g. {@code openai-compatible})
 * @param endpoint       base URL (never contains credentials)
 * @param maskedApiKey   masked credential hint for administrators, {@code null} when no key is configured
 */
public record ProviderDescriptor(String id,
                                 String displayName,
                                 String type,
                                 ProviderKind kind,
                                 Set<LlmCapability> capabilities,
                                 String endpoint,
                                 String maskedApiKey,
                                 ProviderDefaults defaults) {

    public ProviderDescriptor {
        capabilities = capabilities == null || capabilities.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(LlmCapability.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
    }

    public boolean isLocal() {
        return kind == ProviderKind.LOCAL;
    }

    public boolean supports(LlmCapability capability) {
        return capabilities.contains(capability);
    }
}
