package com.shukla.gavel.auction.infrastructure;

import com.shukla.gavel.common.event.BidPlacedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Registry of open SSE connections per auction. Every instance of auction-service holds
 * only its own connections; the Kafka fan-out listener delivers each BidPlacedEvent to
 * every instance, so any pod can serve any subscriber without sticky sessions.
 * Watcher counts are therefore per-instance (documented in ADR 0011).
 */
@Slf4j
@Component
public class BidStreamBroadcaster {

    private static final int MAX_WATCHERS_PER_AUCTION = 200;

    private final Map<UUID, Set<SseEmitter>> emittersByAuction = new ConcurrentHashMap<>();

    public void register(final UUID auctionId, final SseEmitter emitter) {
        final Set<SseEmitter> emitters =
                emittersByAuction.computeIfAbsent(auctionId, id -> new CopyOnWriteArraySet<>());
        if (emitters.size() >= MAX_WATCHERS_PER_AUCTION) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many watchers for auction: " + auctionId);
        }
        emitters.add(emitter);
        emitter.onCompletion(() -> deregister(auctionId, emitter));
        emitter.onTimeout(() -> deregister(auctionId, emitter));
        emitter.onError(error -> deregister(auctionId, emitter));
        broadcastWatchers(auctionId);
    }

    public int watcherCount(final UUID auctionId) {
        final Set<SseEmitter> emitters = emittersByAuction.get(auctionId);
        return emitters == null ? 0 : emitters.size();
    }

    public void broadcastBid(final BidPlacedEvent event) {
        broadcast(event.auctionId(), SseEmitter.event()
                .name("bid")
                .id(event.bidId().toString())
                .data(event, MediaType.APPLICATION_JSON));
    }

    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        emittersByAuction.keySet().forEach(auctionId ->
                broadcast(auctionId, SseEmitter.event().name("heartbeat").data("{}")));
    }

    private void broadcastWatchers(final UUID auctionId) {
        broadcast(auctionId, SseEmitter.event()
                .name("watchers")
                .data(Map.of("count", watcherCount(auctionId)), MediaType.APPLICATION_JSON));
    }

    private void broadcast(final UUID auctionId, final SseEmitter.SseEventBuilder event) {
        final Set<SseEmitter> emitters = emittersByAuction.get(auctionId);
        if (emitters == null) {
            return;
        }
        for (final SseEmitter emitter : emitters) {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException sendFailure) {
                deregister(auctionId, emitter);
            }
        }
    }

    private void deregister(final UUID auctionId, final SseEmitter emitter) {
        final Set<SseEmitter> emitters = emittersByAuction.get(auctionId);
        if (emitters != null && emitters.remove(emitter)) {
            broadcastWatchers(auctionId);
            if (emitters.isEmpty()) {
                emittersByAuction.remove(auctionId, emitters);
            }
        }
    }
}
