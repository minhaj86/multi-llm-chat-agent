package com.example.aichat.api.admin;

import com.example.aichat.api.admin.AdminDtos.AdminProviderResponse;
import com.example.aichat.api.admin.AdminDtos.ProviderSettingsRequest;
import com.example.aichat.application.auth.CurrentUser;
import com.example.aichat.application.provider.ProviderAdministrationService;
import com.example.aichat.application.provider.ProviderSettingsUpdate;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provider administration: status, configuration (credentials masked) and runtime-tunable settings.
 */
@RestController
@RequestMapping("/api/admin/providers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProviderController {

    private final ProviderAdministrationService administration;

    public AdminProviderController(ProviderAdministrationService administration) {
        this.administration = administration;
    }

    @GetMapping
    public List<AdminProviderResponse> list() {
        return administration.list().stream().map(AdminProviderResponse::of).toList();
    }

    @GetMapping("/{provider}")
    public AdminProviderResponse get(@PathVariable("provider") String provider) {
        return AdminProviderResponse.of(administration.get(provider));
    }

    @PutMapping("/{provider}/settings")
    public AdminProviderResponse update(@AuthenticationPrincipal CurrentUser actor,
                                        @PathVariable("provider") String provider,
                                        @Valid @RequestBody ProviderSettingsRequest request) {
        ProviderSettingsUpdate update = new ProviderSettingsUpdate(request.enabled(), request.defaultModel(),
                request.timeoutSeconds(), request.temperature(), request.maxTokens());
        return AdminProviderResponse.of(administration.update(provider, update, actor.username()));
    }

    /** Removes all overrides; the provider uses its configured defaults again. */
    @DeleteMapping("/{provider}/settings")
    public AdminProviderResponse reset(@PathVariable("provider") String provider) {
        return AdminProviderResponse.of(administration.reset(provider));
    }

    @PostMapping("/{provider}/health-check")
    public AdminProviderResponse healthCheck(@PathVariable("provider") String provider) {
        return AdminProviderResponse.of(administration.checkHealth(provider));
    }
}
