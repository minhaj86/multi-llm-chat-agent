package com.example.aichat.api.prompt;

import com.example.aichat.application.prompt.SystemPromptCommand;
import com.example.aichat.domain.prompt.SystemPrompt;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * System prompt bodies. Users see name and description; administrators also see and edit the prompt text.
 */
public final class SystemPromptDtos {

    private SystemPromptDtos() {
    }

    public record SystemPromptSummary(UUID id, String name, String description, boolean isDefault) {

        public static SystemPromptSummary of(SystemPrompt prompt) {
            return new SystemPromptSummary(prompt.id(), prompt.name(), prompt.description(), prompt.defaultPrompt());
        }
    }

    public record SystemPromptResponse(UUID id,
                                       String name,
                                       String description,
                                       String content,
                                       boolean isDefault,
                                       boolean enabled,
                                       int sortOrder,
                                       Instant createdAt,
                                       Instant updatedAt) {

        public static SystemPromptResponse of(SystemPrompt prompt) {
            return new SystemPromptResponse(prompt.id(), prompt.name(), prompt.description(), prompt.content(),
                    prompt.defaultPrompt(), prompt.enabled(), prompt.sortOrder(), prompt.createdAt(),
                    prompt.updatedAt());
        }
    }

    public record SystemPromptRequest(@NotBlank @Size(max = 100) String name,
                                      @Size(max = 500) String description,
                                      @NotBlank @Size(max = 20_000) String content,
                                      boolean isDefault,
                                      Boolean enabled,
                                      Integer sortOrder) {

        public SystemPromptCommand toCommand() {
            return new SystemPromptCommand(name, description, content, isDefault, enabled == null || enabled,
                    sortOrder == null ? 100 : sortOrder);
        }
    }
}
