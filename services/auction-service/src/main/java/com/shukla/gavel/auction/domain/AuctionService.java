package com.shukla.gavel.auction.domain;

import com.shukla.gavel.auction.api.AuctionResponse;
import com.shukla.gavel.auction.api.CreateAuctionRequest;
import com.shukla.gavel.auction.api.PlaceBidRequest;
import com.shukla.gavel.auction.api.PlaceBidResponse;
import com.shukla.gavel.auction.infrastructure.BidCommandPublisher;
import com.shukla.gavel.common.event.PlaceBidCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class AuctionService {

    // Anti-snipe: a bid in the closing minute pushes endsAt out by another minute, so a
    // bid war can't be ended by an auction simply ticking over.
    private static final Duration EXTENSION_WINDOW = Duration.ofSeconds(60);
    private static final Duration EXTENSION_DURATION = Duration.ofSeconds(60);

    private final AuctionRepository auctionRepository;
    private final BidCommandPublisher bidCommandPublisher;

    public AuctionService(final AuctionRepository auctionRepository,
                          final BidCommandPublisher bidCommandPublisher) {
        this.auctionRepository = auctionRepository;
        this.bidCommandPublisher = bidCommandPublisher;
    }

    @Transactional
    public AuctionResponse createAuction(final CreateAuctionRequest request, final String sellerId) {
        final Auction auction = new Auction(
                request.title(), request.description(), sellerId,
                request.reservePriceCents(), request.endsAt());
        final Auction saved = auctionRepository.save(auction);
        log.debug("Auction created: id={} seller={}", saved.getId(), sellerId);
        return toResponse(saved);
    }

    public List<AuctionResponse> listOpenAuctions() {
        return auctionRepository.findByStatus(AuctionStatus.OPEN)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public AuctionResponse getAuction(final UUID id) {
        return auctionRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found: " + id));
    }

    @Transactional
    public AuctionResponse closeAuction(final UUID id, final String requesterId) {
        final Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found: " + id));
        if (!auction.getSellerId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the seller can close this auction");
        }
        auction.close();
        log.debug("Auction closed: id={}", id);
        return toResponse(auction);
    }

    @Transactional
    public PlaceBidResponse placeBid(final UUID auctionId, final String bidderId,
                                     final PlaceBidRequest request) {
        final Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found: " + auctionId));
        if (auction.getStatus() != AuctionStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Auction is not open: " + auctionId);
        }
        if (auction.hasEnded(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Auction has ended: " + auctionId);
        }
        final PlaceBidCommand command = new PlaceBidCommand(
                UUID.randomUUID(), auctionId, bidderId, request.amountCents(), Instant.now());
        bidCommandPublisher.publish(command);
        log.debug("PlaceBidCommand published: auctionId={} bidder={} amount={}",
                auctionId, bidderId, request.amountCents());
        return new PlaceBidResponse(auctionId, bidderId, request.amountCents());
    }

    @Transactional
    public PriceUpdateResult updateCurrentPrice(final UUID auctionId, final long amountCents) {
        return auctionRepository.findById(auctionId).map(auction -> {
            final boolean priceUpdated = auction.updateCurrentPrice(amountCents);
            if (!priceUpdated) {
                log.debug("Price update rejected (stale or non-increasing): auctionId={} amount={} current={}",
                        auctionId, amountCents, auction.getCurrentPriceCents());
                return new PriceUpdateResult(false, false, auction.getEndsAt());
            }
            log.debug("Current price updated: auctionId={} amount={}", auctionId, amountCents);
            final boolean extended = auction.isWithinExtensionWindow(Instant.now(), EXTENSION_WINDOW);
            if (extended) {
                auction.extend(EXTENSION_DURATION);
                log.debug("Anti-snipe extension: auctionId={} newEndsAt={}", auctionId, auction.getEndsAt());
            }
            return new PriceUpdateResult(true, extended, auction.getEndsAt());
        }).orElseGet(PriceUpdateResult::notFound);
    }

    /**
     * Closes every OPEN auction whose endsAt has passed. Locks rows with
     * FOR UPDATE SKIP LOCKED so multiple auction-service replicas running this sweep
     * concurrently never double-close the same row. Returns the ids closed in this sweep
     * so the caller can publish AuctionClosedEvent for each — done outside this method
     * so the DB commit and the Kafka publish are never in the same transaction.
     */
    @Transactional
    public List<UUID> closeAuctionsPastEndsAt(final Instant cutoff) {
        final List<Auction> due = auctionRepository.lockOpenAuctionsEndingBy(cutoff);
        final List<UUID> closedIds = new ArrayList<>();
        for (final Auction auction : due) {
            auction.close();
            closedIds.add(auction.getId());
            log.debug("Auto-closed auction at endsAt: id={} endsAt={}", auction.getId(), auction.getEndsAt());
        }
        return closedIds;
    }

    private AuctionResponse toResponse(final Auction auction) {
        return new AuctionResponse(
                auction.getId(),
                auction.getTitle(),
                auction.getDescription(),
                auction.getSellerId(),
                auction.getStatus(),
                auction.getReservePriceCents(),
                auction.getCurrentPriceCents(),
                auction.getEndsAt(),
                auction.getCreatedAt());
    }
}
