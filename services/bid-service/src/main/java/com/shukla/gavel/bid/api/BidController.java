package com.shukla.gavel.bid.api;

import com.shukla.gavel.bid.domain.Bid;
import com.shukla.gavel.bid.domain.BidRepository;
import com.shukla.gavel.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class BidController {

    private final BidRepository bidRepository;

    public BidController(final BidRepository bidRepository) {
        this.bidRepository = bidRepository;
    }

    @GetMapping("/bids")
    public ApiResponse<List<BidSummary>> bids(
            @RequestParam(name = "auctionId", required = false) final UUID auctionId) {
        log.debug("bids called: auctionId={}", auctionId);
        final List<Bid> bids = auctionId == null
                ? bidRepository.findAll()
                : bidRepository.findTop50ByAuctionIdOrderByPlacedAtDesc(auctionId);
        return ApiResponse.of(bids.stream().map(this::toSummary).toList());
    }

    private BidSummary toSummary(final Bid bid) {
        return new BidSummary(
                bid.getId().toString(),
                bid.getAuctionId().toString(),
                bid.getBidderId(),
                bid.getAmountCents(),
                bid.getPlacedAt());
    }
}
