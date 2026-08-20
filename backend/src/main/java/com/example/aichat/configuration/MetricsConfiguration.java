package com.example.aichat.configuration;

import com.example.aichat.application.chat.GenerationRegistry;
import com.example.aichat.application.provider.LlmProviderRegistry;
import com.example.aichat.application.provider.ProviderHealthMonitor;
import com.example.aichat.domain.llm.LlmProvider;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Gauges complementing the per-request LLM metrics: provider availability ({@code llm_provider_up}) and in-flight
 * generations ({@code llm_active_generations}).
 */
@Configuration(proxyBeanMethods = false)
public class MetricsConfiguration {

    private final MeterRegistry registry;
    private final LlmProviderRegistry providers;
    private final ProviderHealthMonitor healthMonitor;
    private final GenerationRegistry generations;

    public MetricsConfiguration(MeterRegistry registry, LlmProviderRegistry providers,
                                ProviderHealthMonitor healthMonitor, GenerationRegistry generations) {
        this.registry = registry;
        this.providers = providers;
        this.healthMonitor = healthMonitor;
        this.generations = generations;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerGauges() {
        for (LlmProvider provider : providers.all()) {
            String id = provider.providerId();
            Gauge.builder("llm.provider.up", healthMonitor, monitor -> monitor.current(id).isAvailable() ? 1 : 0)
                    .description("1 when the provider is reachable, 0 otherwise")
                    .tag("provider", id)
                    .register(registry);
        }
        Gauge.builder("llm.active.generations", generations, GenerationRegistry::activeCount)
                .description("Chat generations currently streaming")
                .register(registry);
    }
}
