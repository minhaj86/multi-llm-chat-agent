package com.example.aichat.api;

import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Server-Sent Events helpers shared by streaming endpoints.
 */
public final class SseSupport {

    private SseSupport() {
    }

    /**
     * Wraps an event stream into a response that disables proxy buffering and interleaves keep-alive comments, so
     * that slow models (e.g. while loading or "thinking") do not trigger idle timeouts in proxies and browsers.
     */
    public static <T> ResponseEntity<Flux<ServerSentEvent<T>>> response(Flux<ServerSentEvent<T>> events,
                                                                         Duration heartbeatInterval) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(withHeartbeat(events, heartbeatInterval));
    }

    public static <T> Flux<ServerSentEvent<T>> withHeartbeat(Flux<ServerSentEvent<T>> events, Duration interval) {
        return Flux.defer(() -> {
            Sinks.One<Boolean> finished = Sinks.one();
            Flux<ServerSentEvent<T>> main = events.doFinally(signal -> finished.tryEmitValue(Boolean.TRUE));
            Flux<ServerSentEvent<T>> heartbeats = Flux.interval(interval, interval)
                    .map(tick -> ServerSentEvent.<T>builder().comment("keep-alive").build())
                    .takeUntilOther(finished.asMono());
            return Flux.merge(main, heartbeats);
        });
    }

    public static <T> ServerSentEvent<T> event(String name, T data) {
        return ServerSentEvent.<T>builder().event(name).data(data).build();
    }
}
