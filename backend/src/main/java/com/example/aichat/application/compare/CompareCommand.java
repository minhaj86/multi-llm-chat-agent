package com.example.aichat.application.compare;

import java.util.List;
import java.util.UUID;

/**
 * Send the same prompt to several provider/model targets.
 */
public record CompareCommand(UUID userId,
                             String prompt,
                             List<Target> targets,
                             UUID systemPromptId,
                             String correlationId) {

    public CompareCommand {
        targets = targets == null ? List.of() : List.copyOf(targets);
    }

    public record Target(String provider, String model) {
    }
}
