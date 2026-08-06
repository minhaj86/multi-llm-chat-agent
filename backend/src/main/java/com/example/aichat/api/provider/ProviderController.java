package com.example.aichat.api.provider;

import com.example.aichat.api.provider.ProviderDtos.HealthResponse;
import com.example.aichat.api.provider.ProviderDtos.ModelResponse;
import com.example.aichat.api.provider.ProviderDtos.ProviderResponse;
import com.example.aichat.application.provider.ProviderCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provider discovery for the UI. The frontend builds its provider/model selector exclusively from these endpoints.
 */
@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final ProviderCatalogService catalog;

    public ProviderController(ProviderCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<ProviderResponse> providers() {
        String defaultProvider = catalog.defaultProviderId();
        return catalog.providers().stream().map(p -> ProviderResponse.of(p, defaultProvider)).toList();
    }

    @GetMapping("/{provider}/models")
    public List<ModelResponse> models(@PathVariable("provider") String provider) {
        return catalog.models(provider).stream().map(ModelResponse::of).toList();
    }

    @GetMapping("/{provider}/health")
    public HealthResponse health(@PathVariable("provider") String provider,
                                 @RequestParam(name = "refresh", defaultValue = "false") boolean refresh) {
        return HealthResponse.of(catalog.health(provider, refresh));
    }
}
