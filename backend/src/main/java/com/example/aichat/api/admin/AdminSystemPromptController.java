package com.example.aichat.api.admin;

import com.example.aichat.api.prompt.SystemPromptDtos.SystemPromptRequest;
import com.example.aichat.api.prompt.SystemPromptDtos.SystemPromptResponse;
import com.example.aichat.application.prompt.SystemPromptService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Management of server-side system prompt profiles.
 */
@RestController
@RequestMapping("/api/admin/system-prompts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSystemPromptController {

    private final SystemPromptService prompts;

    public AdminSystemPromptController(SystemPromptService prompts) {
        this.prompts = prompts;
    }

    @GetMapping
    public List<SystemPromptResponse> list() {
        return prompts.listAll().stream().map(SystemPromptResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SystemPromptResponse create(@Valid @RequestBody SystemPromptRequest request) {
        return SystemPromptResponse.of(prompts.create(request.toCommand()));
    }

    @PutMapping("/{id}")
    public SystemPromptResponse update(@PathVariable("id") UUID id, @Valid @RequestBody SystemPromptRequest request) {
        return SystemPromptResponse.of(prompts.update(id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") UUID id) {
        prompts.delete(id);
    }
}
