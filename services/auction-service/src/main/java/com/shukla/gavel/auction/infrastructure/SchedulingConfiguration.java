package com.shukla.gavel.auction.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables @Scheduled processing — used by the SSE heartbeat in BidStreamBroadcaster.
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
