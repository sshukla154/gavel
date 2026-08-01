package com.shukla.gavel.auction.domain;

import com.shukla.gavel.auction.api.AuctionResponse;
import com.shukla.gavel.auction.api.CreateAuctionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class AuctionService {

    private final AuctionRepository auctionRepository;

    public AuctionService(final AuctionRepository auctionRepository) {
        this.auctionRepository = auctionRepository;
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
