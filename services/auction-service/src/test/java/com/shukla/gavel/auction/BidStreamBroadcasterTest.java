package com.shukla.gavel.auction;

import com.shukla.gavel.auction.infrastructure.BidStreamBroadcaster;
import com.shukla.gavel.common.event.BidPlacedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BidStreamBroadcasterTest {

    private final BidStreamBroadcaster broadcaster = new BidStreamBroadcaster();

    /** Records sends instead of writing to a servlet response. */
    private static final class RecordingEmitter extends SseEmitter {
        private final List<SseEventBuilder> sent = new ArrayList<>();
        private boolean failOnSend;

        RecordingEmitter() {
            super(60_000L);
        }

        @Override
        public void send(final SseEventBuilder builder) throws IOException {
            if (failOnSend) {
                throw new IOException("client gone");
            }
            sent.add(builder);
        }
    }

    private static BidPlacedEvent eventFor(final UUID auctionId) {
        return new BidPlacedEvent(UUID.randomUUID(), auctionId, "bidder-1", 120_000L, Instant.now());
    }

    @Test
    void registerIncreasesWatcherCountAndNotifiesWatchers() {
        final UUID auctionId = UUID.randomUUID();
        final RecordingEmitter emitter = new RecordingEmitter();

        broadcaster.register(auctionId, emitter);

        assertThat(broadcaster.watcherCount(auctionId)).isEqualTo(1);
        assertThat(emitter.sent).hasSize(1);
    }

    @Test
    void broadcastBidReachesOnlyWatchersOfThatAuction() {
        final UUID auctionId = UUID.randomUUID();
        final UUID otherAuctionId = UUID.randomUUID();
        final RecordingEmitter watcher = new RecordingEmitter();
        final RecordingEmitter otherWatcher = new RecordingEmitter();
        broadcaster.register(auctionId, watcher);
        broadcaster.register(otherAuctionId, otherWatcher);
        final int sentBefore = watcher.sent.size();
        final int otherSentBefore = otherWatcher.sent.size();

        broadcaster.broadcastBid(eventFor(auctionId));

        assertThat(watcher.sent).hasSize(sentBefore + 1);
        assertThat(otherWatcher.sent).hasSize(otherSentBefore);
    }

    @Test
    void failingEmitterIsDeregistered() {
        final UUID auctionId = UUID.randomUUID();
        final RecordingEmitter dying = new RecordingEmitter();
        final RecordingEmitter healthy = new RecordingEmitter();
        broadcaster.register(auctionId, dying);
        broadcaster.register(auctionId, healthy);
        dying.failOnSend = true;

        broadcaster.broadcastBid(eventFor(auctionId));

        assertThat(broadcaster.watcherCount(auctionId)).isEqualTo(1);
        broadcaster.broadcastBid(eventFor(auctionId));
        assertThat(broadcaster.watcherCount(auctionId)).isEqualTo(1);
    }

    @Test
    void registerBeyondCapacityIsRejected() {
        final UUID auctionId = UUID.randomUUID();
        for (int i = 0; i < 200; i++) {
            broadcaster.register(auctionId, new RecordingEmitter());
        }

        assertThatThrownBy(() -> broadcaster.register(auctionId, new RecordingEmitter()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429");
    }

    @Test
    void heartbeatDropsDeadWatchers() {
        final UUID auctionId = UUID.randomUUID();
        final RecordingEmitter dying = new RecordingEmitter();
        broadcaster.register(auctionId, dying);
        dying.failOnSend = true;

        broadcaster.heartbeat();

        assertThat(broadcaster.watcherCount(auctionId)).isZero();
    }
}
