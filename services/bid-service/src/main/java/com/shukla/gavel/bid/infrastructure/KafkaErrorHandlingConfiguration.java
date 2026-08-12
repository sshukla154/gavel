package com.shukla.gavel.bid.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Poison-pill protection for the command listener: without this, a message that fails
 * repeatedly blocks its partition forever — one bad bid command halts every auction
 * hashed to that partition. Failed records are retried with exponential backoff, then
 * published to <topic>.DLT (same partition) by the recoverer. Deserialization failures
 * (see ErrorHandlingDeserializer in application.yaml) skip the retries and go straight
 * to the DLT. Reordering introduced by redelivery is harmless here: commandId
 * idempotency dedupes and the price guard is monotonic (ADR 0010).
 */
@Configuration
public class KafkaErrorHandlingConfiguration {

    private static final long INITIAL_BACKOFF_MILLIS = 500L;
    private static final long MAX_BACKOFF_MILLIS = 4_000L;
    private static final int MAX_RETRIES = 3;

    @Bean
    public CommonErrorHandler kafkaErrorHandler(final KafkaTemplate<Object, Object> kafkaTemplate) {
        final DeadLetterPublishingRecoverer deadLetterRecoverer =
                new DeadLetterPublishingRecoverer(kafkaTemplate);
        final ExponentialBackOff backOff = new ExponentialBackOff(INITIAL_BACKOFF_MILLIS, 2.0);
        backOff.setMaxInterval(MAX_BACKOFF_MILLIS);
        backOff.setMaxAttempts(MAX_RETRIES);
        return new DefaultErrorHandler(deadLetterRecoverer, backOff);
    }
}
