package com.runtimeerror.sentinel.console;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** GET /api/stream/decisions: the live feed, as Server-Sent Events. See DecisionStream. */
@RestController
public class LiveFeedController {

    private final DecisionStream stream;

    public LiveFeedController(DecisionStream stream) {
        this.stream = stream;
    }

    @GetMapping(path = "/api/stream/decisions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter decisions() {
        return stream.subscribe();
    }
}
