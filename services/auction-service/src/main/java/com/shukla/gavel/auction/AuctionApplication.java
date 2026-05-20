package com.shukla.gavel.auction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for auction-service.
 *
 * <p>Manages auctions, bids, and visit tracking for the Gavel real-time auction platform.
 */
@SpringBootApplication
public class AuctionApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments forwarded to Spring's environment
     */
    public static void main(final String[] args) {
        SpringApplication.run(AuctionApplication.class, args);
    }
}
