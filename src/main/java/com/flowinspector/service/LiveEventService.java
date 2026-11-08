package com.flowinspector.service;

import com.flowinspector.domain.Observation;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Owns live SSE subscriptions. The repository only stores data. */
@Service
public class LiveEventService {
    private final Set<SseEmitter> clients = ConcurrentHashMap.newKeySet();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        clients.add(emitter);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onError(error -> clients.remove(emitter));
        emitter.onTimeout(() -> clients.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ready"));
        } catch (IOException | IllegalStateException e) {
            disconnect(emitter);
        }
        return emitter;
    }

    public void publish(Observation observation) {
        for (SseEmitter client : clients) {
            try {
                client.send(SseEmitter.event().name("observation")
                        .data(Map.of("traceId", observation.traceId(), "eventId", observation.eventId())));
            } catch (IOException | IllegalStateException e) {
                disconnect(client);
            }
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void heartbeat() {
        for (SseEmitter client : clients) {
            try {
                client.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException e) {
                disconnect(client);
            }
        }
    }

    private void disconnect(SseEmitter client) {
        clients.remove(client);
        client.complete();
    }
}
