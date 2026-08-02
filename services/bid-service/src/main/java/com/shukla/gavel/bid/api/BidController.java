package com.shukla.gavel.bid.api;

import com.shukla.gavel.bid.domain.BidRepository;
import com.shukla.gavel.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class BidController {

    private final BidRepository bidRepository;

    public BidController(final BidRepository bidRepository) {
        this.bidRepository = bidRepository;
    }

    @GetMapping("/bids")
    public ApiResponse<List<BidSummary>> bids() {
        log.debug("bids called");
        final List<BidSummary> bids = bidRepository.findAll().stream()
                .map(bid -> new BidSummary(
                        bid.getId().toString(),
                        bid.getAuctionId().toString(),
                        bid.getBidderId(),
                        bid.getAmountCents(),
                        bid.getPlacedAt()))
                .toList();
        return ApiResponse.of(bids);
    }
}
