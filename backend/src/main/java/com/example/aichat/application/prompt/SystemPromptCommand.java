package com.example.aichat.application.prompt;

/**
 * Create/update command for a system prompt profile.
 */
public record SystemPromptCommand(String name,
                                  String description,
                                  String content,
                                  boolean defaultPrompt,
                                  boolean enabled,
                                  int sortOrder) {
}
