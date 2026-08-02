package com.shukla.gavel.auction;

import com.shukla.gavel.common.event.BidPlacedEvent;
import com.shukla.gavel.common.event.PlaceBidCommand;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fast regression guard for the Kafka wire format: the configured serde must round-trip
 * both event records, including their {@link Instant} fields. A break here fails in
 * milliseconds with a clear message instead of as an awaitility timeout inside a
 * container-based integration test.
 */
class EventSerdeTest {

    @Test
    void bidPlacedEventRoundTrips() {
        final BidPlacedEvent event = new BidPlacedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "bidder-1", 120_000L,
                Instant.now().truncatedTo(ChronoUnit.MILLIS));

        try (JacksonJsonSerializer<BidPlacedEvent> serializer = new JacksonJsonSerializer<>();
             JacksonJsonDeserializer<BidPlacedEvent> deserializer =
                     new JacksonJsonDeserializer<>(BidPlacedEvent.class)) {
            final byte[] bytes = serializer.serialize("auction.bids.events", event);
            final BidPlacedEvent roundTripped = deserializer.deserialize("auction.bids.events", bytes);

            assertThat(roundTripped).isEqualTo(event);
        }
    }

    @Test
    void placeBidCommandRoundTrips() {
        final PlaceBidCommand command = new PlaceBidCommand(
                UUID.randomUUID(), UUID.randomUUID(), "bidder-1", 120_000L,
                Instant.now().truncatedTo(ChronoUnit.MILLIS));

        try (JacksonJsonSerializer<PlaceBidCommand> serializer = new JacksonJsonSerializer<>();
             JacksonJsonDeserializer<PlaceBidCommand> deserializer =
                     new JacksonJsonDeserializer<>(PlaceBidCommand.class)) {
            final byte[] bytes = serializer.serialize("auction.bids.commands", command);
            final PlaceBidCommand roundTripped = deserializer.deserialize("auction.bids.commands", bytes);

            assertThat(roundTripped).isEqualTo(command);
        }
    }
}
