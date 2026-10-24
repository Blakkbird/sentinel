package com.runtimeerror.sentinel.console;

import com.runtimeerror.sentinel.decision.DecisionRecorded;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The live feed: pushes every new decision to every open console tab.
 *
 * It uses Server-Sent Events (SSE). The browser makes one ordinary GET request and the
 * server never finishes the response; it keeps writing small text events into it, one per
 * decision. Simpler than WebSockets, because data only flows one way (server to browser),
 * and the browser reconnects by itself if the connection drops.
 *
 * Three details that matter:
 *  - One background thread does all the sending, so a decision never waits for a slow
 *    browser. If browsers fall far behind, the oldest queued events are dropped rather than
 *    letting the queue grow without limit: for a live view, fresh beats complete.
 *  - A comment line every 20 seconds keeps idle connections open through proxies, and is
 *    how a closed browser tab gets noticed (the write fails).
 *  - On shutdown, every stream is closed first. Otherwise graceful shutdown would wait up
 *    to 30 seconds for these never-ending responses to finish.
 *
 * Everything lives in this one process. With several servers, each would only see its own
 * decisions; that is a problem for later (Redis pub/sub would solve it).
 */
@Component
public class DecisionStream {

    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private final ThreadPoolExecutor sender = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1_000),
            Thread.ofPlatform().name("live-feed").daemon(true).factory(),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    /** Opens a stream for one browser tab. */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);   // after 30 minutes the browser simply reconnects
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> emitters.remove(emitter));
        emitters.add(emitter);
        try {
            emitter.send(SseEmitter.event().name("ready").data("ok"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    @EventListener
    public void onDecision(DecisionRecorded event) {
        if (emitters.isEmpty()) {
            return;
        }
        DecisionSummary summary = DecisionSummary.from(event.decision());
        sender.execute(() -> broadcast(() -> SseEmitter.event()
                .name("decision")
                .id(summary.decisionId().toString())
                .data(summary, MediaType.APPLICATION_JSON)));
    }

    @Scheduled(fixedRate = 20_000)
    public void keepAlive() {
        if (!emitters.isEmpty()) {
            sender.execute(() -> broadcast(() -> SseEmitter.event().comment("keep-alive")));
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void closeAll() {
        emitters.forEach(SseEmitter::complete);
        emitters.clear();
        sender.shutdownNow();
    }

    /**
     * Sends one event to every open stream. Each stream gets its own freshly built event: an
     * event builder collects its text as it builds, so sharing one between streams would
     * send garbled events.
     */
    private void broadcast(Supplier<SseEmitter.SseEventBuilder> event) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(event.get());
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);   // the tab was closed or the stream already ended
            }
        }
    }
}
