package com.shukla.gavel.bid.api;

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

    @GetMapping("/bids")
    public ApiResponse<List<BidSummary>> bids() {
        log.debug("bids called");
        return ApiResponse.of(List.of(
                new BidSummary("auction-001", "bidder-1", 10000L, "PENDING"),
                new BidSummary("auction-002", "bidder-2", 25000L, "ACCEPTED")
        ));
    }
}
