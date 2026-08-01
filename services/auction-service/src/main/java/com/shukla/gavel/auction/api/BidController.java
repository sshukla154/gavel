package com.shukla.gavel.auction.api;

import com.shukla.gavel.auction.infrastructure.BidClient;
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

    private final BidClient bidClient;

    public BidController(final BidClient bidClient) {
        this.bidClient = bidClient;
    }

    @GetMapping("/bids")
    public ApiResponse<List<BidSummary>> bids() {
        log.debug("bids called — relaying to bid-service");
        return ApiResponse.of(bidClient.fetchBids());
    }
}
