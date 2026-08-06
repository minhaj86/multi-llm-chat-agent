package com.example.aichat.api.prompt;

import com.example.aichat.api.prompt.SystemPromptDtos.SystemPromptSummary;
import com.example.aichat.application.prompt.SystemPromptService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Selectable system prompt profiles.
 */
@RestController
@RequestMapping("/api/system-prompts")
public class SystemPromptController {

    private final SystemPromptService prompts;

    public SystemPromptController(SystemPromptService prompts) {
        this.prompts = prompts;
    }

    @GetMapping
    public List<SystemPromptSummary> list() {
        return prompts.listEnabled().stream().map(SystemPromptSummary::of).toList();
    }
}
