package com.example.aichat.application.prompt;

import com.example.aichat.application.common.ConflictException;
import com.example.aichat.application.common.ResourceNotFoundException;
import com.example.aichat.application.common.ValidationException;
import com.example.aichat.common.Texts;
import com.example.aichat.common.Times;
import com.example.aichat.domain.prompt.SystemPrompt;
import com.example.aichat.domain.prompt.SystemPromptRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-side catalogue of system prompt profiles.
 */
@Service
public class SystemPromptService {

    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_DESCRIPTION_LENGTH = 500;
    static final int MAX_CONTENT_LENGTH = 20_000;

    private final SystemPromptRepository repository;
    private final Clock clock;

    public SystemPromptService(SystemPromptRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public List<SystemPrompt> listEnabled() {
        return repository.findAll().stream().filter(SystemPrompt::enabled).toList();
    }

    public List<SystemPrompt> listAll() {
        return repository.findAll();
    }

    public SystemPrompt get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("System prompt", id));
    }

    /** Validates that a user may select the prompt (exists and is enabled). */
    public SystemPrompt requireSelectable(UUID id) {
        SystemPrompt prompt = get(id);
        if (!prompt.enabled()) {
            throw new ValidationException("PROMPT_DISABLED", "The system prompt '" + prompt.name() + "' is disabled");
        }
        return prompt;
    }

    public Optional<SystemPrompt> defaultPrompt() {
        return repository.findDefault().filter(SystemPrompt::enabled);
    }

    /**
     * Prompt text used for a conversation: its selected profile when still enabled, otherwise the default profile.
     */
    public Optional<String> contentFor(UUID promptId) {
        Optional<SystemPrompt> selected = Optional.ofNullable(promptId)
                .flatMap(repository::findById)
                .filter(SystemPrompt::enabled);
        return selected.or(this::defaultPrompt).map(SystemPrompt::content);
    }

    @Transactional
    public SystemPrompt create(SystemPromptCommand command) {
        validate(command, null);
        Instant now = Times.now(clock);
        if (command.defaultPrompt()) {
            repository.clearDefault();
        }
        return repository.save(SystemPrompt.create(command.name(), Texts.blankToNull(command.description()),
                command.content(), command.defaultPrompt(), command.enabled(), command.sortOrder(), now));
    }

    @Transactional
    public SystemPrompt update(UUID id, SystemPromptCommand command) {
        SystemPrompt existing = get(id);
        validate(command, id);
        if (command.defaultPrompt() && !existing.defaultPrompt()) {
            repository.clearDefault();
        }
        return repository.save(existing.update(command.name(), Texts.blankToNull(command.description()),
                command.content(), command.defaultPrompt(), command.enabled(), command.sortOrder(), Times.now(clock)));
    }

    @Transactional
    public void delete(UUID id) {
        get(id);
        repository.deleteById(id);
    }

    private void validate(SystemPromptCommand command, UUID excludingId) {
        if (Texts.isBlank(command.name()) || command.name().strip().length() > MAX_NAME_LENGTH) {
            throw new ValidationException("INVALID_NAME", "Name is required and must not exceed "
                    + MAX_NAME_LENGTH + " characters");
        }
        if (command.description() != null && command.description().length() > MAX_DESCRIPTION_LENGTH) {
            throw new ValidationException("INVALID_DESCRIPTION", "Description must not exceed "
                    + MAX_DESCRIPTION_LENGTH + " characters");
        }
        if (Texts.isBlank(command.content()) || command.content().length() > MAX_CONTENT_LENGTH) {
            throw new ValidationException("INVALID_CONTENT", "Prompt text is required and must not exceed "
                    + MAX_CONTENT_LENGTH + " characters");
        }
        if (command.defaultPrompt() && !command.enabled()) {
            throw new ValidationException("INVALID_DEFAULT", "The default prompt must be enabled");
        }
        if (repository.existsByNameIgnoreCase(command.name().strip(), excludingId)) {
            throw new ConflictException("PROMPT_NAME_TAKEN", "A system prompt named '" + command.name().strip()
                    + "' already exists");
        }
    }
}
