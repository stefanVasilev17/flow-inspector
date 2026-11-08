package com.flowinspector.controller;

import com.flowinspector.service.LiveEventService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
public class LiveEventsController {
    private final LiveEventService liveEventService;

    public LiveEventsController(LiveEventService liveEventService) {
        this.liveEventService = liveEventService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return liveEventService.subscribe();
    }
}
