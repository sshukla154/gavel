package com.shukla.gavel.auction.api;

import com.shukla.gavel.auction.domain.AuctionService;
import com.shukla.gavel.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auctions")
public class AuctionController {

    private final AuctionService auctionService;

    public AuctionController(final AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuctionResponse> create(
            @RequestBody final CreateAuctionRequest request,
            @AuthenticationPrincipal final Jwt jwt) {
        return ApiResponse.of(auctionService.createAuction(request, jwt.getSubject()));
    }

    @GetMapping
    public ApiResponse<List<AuctionResponse>> list() {
        return ApiResponse.of(auctionService.listOpenAuctions());
    }

    @GetMapping("/{id}")
    public ApiResponse<AuctionResponse> get(@PathVariable final UUID id) {
        return ApiResponse.of(auctionService.getAuction(id));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<AuctionResponse> close(
            @PathVariable final UUID id,
            @AuthenticationPrincipal final Jwt jwt) {
        return ApiResponse.of(auctionService.closeAuction(id, jwt.getSubject()));
    }

    @PostMapping("/{id}/bids")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<PlaceBidResponse> placeBid(
            @PathVariable final UUID id,
            @RequestBody final PlaceBidRequest request,
            @AuthenticationPrincipal final Jwt jwt) {
        return ApiResponse.of(auctionService.placeBid(id, jwt.getSubject(), request));
    }
}
