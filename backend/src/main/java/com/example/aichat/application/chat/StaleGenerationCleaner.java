package com.example.aichat.application.chat;

import com.example.aichat.common.Times;
import com.example.aichat.domain.conversation.MessageRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Marks assistant messages that stayed in {@code STREAMING} far longer than any generation can take (e.g. after a
 * crash or redeploy) as failed, so the UI never shows an eternal spinner.
 */
@Component
public class StaleGenerationCleaner {

    private static final Logger log = LoggerFactory.getLogger(StaleGenerationCleaner.class);

    private final MessageRepository messages;
    private final Clock clock;
    private final Duration maxAge;

    public StaleGenerationCleaner(MessageRepository messages, Clock clock,
                                  @Value("${chat.stale-generation-age:PT30M}") Duration maxAge) {
        this.messages = messages;
        this.clock = clock;
        this.maxAge = maxAge;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT5M")
    public void failStaleGenerations() {
        Instant now = Times.now(clock);
        int updated = messages.failStaleStreaming(now.minus(maxAge), now);
        if (updated > 0) {
            log.warn("Marked {} interrupted generation(s) as failed", updated);
        }
    }
}
