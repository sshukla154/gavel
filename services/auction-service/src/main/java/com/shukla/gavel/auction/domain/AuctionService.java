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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class AuctionService {

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
        final PlaceBidCommand command = new PlaceBidCommand(
                auctionId, bidderId, request.amountCents(), Instant.now());
        bidCommandPublisher.publish(command);
        log.debug("PlaceBidCommand published: auctionId={} bidder={} amount={}",
                auctionId, bidderId, request.amountCents());
        return new PlaceBidResponse(auctionId, bidderId, request.amountCents());
    }

    @Transactional
    public void updateCurrentPrice(final UUID auctionId, final long amountCents) {
        auctionRepository.findById(auctionId).ifPresent(auction -> {
            auction.updateCurrentPrice(amountCents);
            log.debug("Current price updated: auctionId={} amount={}", auctionId, amountCents);
        });
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
