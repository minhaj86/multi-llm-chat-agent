package com.example.aichat.application.chat;

import com.example.aichat.application.common.TooManyRequestsException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Tracks in-flight generations so that users can stop them explicitly (in addition to closing the stream) and
 * limits concurrent generations per user.
 */
@Component
public class GenerationRegistry {

    private final Map<UUID, ActiveGeneration> active = new ConcurrentHashMap<>();
    private final int maxPerUser;

    public GenerationRegistry(ChatProperties properties) {
        this.maxPerUser = properties.maxConcurrentGenerationsPerUser();
    }

    public void ensureCapacity(UUID userId) {
        if (countFor(userId) >= maxPerUser) {
            throw new TooManyRequestsException("Too many concurrent generations. Wait for a response to finish "
                    + "or stop it.", Duration.ofSeconds(5));
        }
    }

    public ActiveGeneration register(UUID generationId, UUID userId) {
        ActiveGeneration generation = new ActiveGeneration(generationId, userId);
        active.put(generationId, generation);
        return generation;
    }

    /** Requests cancellation; returns {@code false} when the generation is unknown or owned by someone else. */
    public boolean cancel(UUID generationId, UUID userId) {
        ActiveGeneration generation = active.get(generationId);
        if (generation == null || !generation.userId().equals(userId)) {
            return false;
        }
        generation.cancel();
        return true;
    }

    public boolean isActive(UUID generationId) {
        return active.containsKey(generationId);
    }

    public void unregister(UUID generationId) {
        active.remove(generationId);
    }

    public int activeCount() {
        return active.size();
    }

    private long countFor(UUID userId) {
        return active.values().stream().filter(g -> g.userId().equals(userId)).count();
    }

    public static final class ActiveGeneration {

        private final UUID id;
        private final UUID userId;
        private final Sinks.One<Boolean> cancelSignal = Sinks.one();
        private final AtomicBoolean cancelRequested = new AtomicBoolean();

        ActiveGeneration(UUID id, UUID userId) {
            this.id = id;
            this.userId = userId;
        }

        public UUID id() {
            return id;
        }

        public UUID userId() {
            return userId;
        }

        public Mono<Boolean> cancellation() {
            return cancelSignal.asMono();
        }

        public boolean isCancelRequested() {
            return cancelRequested.get();
        }

        void cancel() {
            if (cancelRequested.compareAndSet(false, true)) {
                cancelSignal.tryEmitValue(Boolean.TRUE);
            }
        }
    }
}
