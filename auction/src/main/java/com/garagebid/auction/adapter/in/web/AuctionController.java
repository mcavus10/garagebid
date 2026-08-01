package com.garagebid.auction.adapter.in.web;

import com.garagebid.auction.adapter.in.web.dto.OpenAuctionRequest;
import com.garagebid.auction.adapter.in.web.dto.PlaceBidRequest;
import com.garagebid.auction.application.port.in.CloseAuctionUseCase;
import com.garagebid.auction.application.port.in.OpenAuctionUseCase;
import com.garagebid.auction.application.port.in.OpenAuctionUseCase.OpenAuctionCommand;
import com.garagebid.auction.application.port.in.PlaceBidUseCase;
import com.garagebid.auction.domain.model.Money;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Currency;
import java.util.UUID;

@Tag(
        name = "Auctions",
        description = "Auction lifecycle and bidding operations"
)
@RestController
@RequestMapping("/api/v1/auctions")
public class AuctionController {

    private final OpenAuctionUseCase openAuctionUseCase;

    private final PlaceBidUseCase placeBidUseCase;

    private final CloseAuctionUseCase closeAuctionUseCase;

    public AuctionController(
            OpenAuctionUseCase openAuctionUseCase,
            PlaceBidUseCase placeBidUseCase,
            CloseAuctionUseCase closeAuctionUseCase
    ) {
        this.openAuctionUseCase = openAuctionUseCase;
        this.placeBidUseCase = placeBidUseCase;
        this.closeAuctionUseCase = closeAuctionUseCase;
    }

    @Operation(
            summary = "Open an auction",
            description = "Creates a new auction in OPEN status"
    )
    @PostMapping
    public ResponseEntity<Void> openAuction(@Valid @RequestBody OpenAuctionRequest request) {
        Money startingPrice= new Money(request.startingAmount(), Currency.getInstance(request.currency()));

        OpenAuctionCommand command = new OpenAuctionCommand(
                request.carId(),
                request.sellerId(),
                startingPrice,
                request.endsAt()
        );

        UUID auctionId = openAuctionUseCase.openAuction(command);

        URI location = URI.create("/api/v1/auctions/" + auctionId);

        return ResponseEntity.created(location).build();
    }

    @Operation(
            summary = "Place a bid",
            description = "Places a bid if the auction is open and the amount is valid"
    )
    @PostMapping("/{auctionId}/bids")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void placeBid(
            @PathVariable UUID auctionId,
            @Valid @RequestBody PlaceBidRequest request
    ) {
        Money amount = new Money(
                request.amount(),
                Currency.getInstance(request.currency())
        );

        PlaceBidUseCase.PlaceBidCommand command = new PlaceBidUseCase.PlaceBidCommand(
                auctionId,
                request.bidderId(),
                amount
        );

        placeBidUseCase.placeBid(command);
    }

    @Operation(
            summary = "Close an auction",
            description = "Closes an open auction"
    )
    @PostMapping("/{auctionId}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void closeAuction(@PathVariable UUID auctionId) {
        closeAuctionUseCase.closeAuction(auctionId);
    }
}
