package com.garagebid.auction.application.service;

import com.garagebid.auction.application.port.in.CloseAuctionUseCase;
import com.garagebid.auction.application.port.in.OpenAuctionUseCase;
import com.garagebid.auction.application.port.in.PlaceBidUseCase;
import com.garagebid.auction.application.port.out.CarLookupPort;
import com.garagebid.auction.application.port.out.LoadAuctionPort;
import com.garagebid.auction.domain.model.Auction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuctionService
        implements PlaceBidUseCase, CloseAuctionUseCase, OpenAuctionUseCase {

    private final LoadAuctionPort loadAuctionPort;
    private final CarLookupPort carLookupPort;
    private final AuctionTransactionalWriter transactionalWriter;
    private final Clock clock;

    public AuctionService(
            LoadAuctionPort loadAuctionPort,
            CarLookupPort carLookupPort,
            AuctionTransactionalWriter transactionalWriter,
            Clock clock
    ) {
        this.loadAuctionPort = loadAuctionPort;
        this.carLookupPort = carLookupPort;
        this.transactionalWriter = transactionalWriter;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void placeBid(PlaceBidCommand command) {
        Auction auction = loadAuctionPort.loadById(command.auctionId())
                .orElseThrow(() -> new AuctionNotFoundException(command.auctionId()));

        auction.placeBid(
                command.bidderId(),
                command.amount(),
                Instant.now(clock)
        );

        transactionalWriter.update(auction);
    }

    @Override
    @Transactional
    public void closeAuction(UUID auctionId) {
        Auction auction = loadAuctionPort.loadById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        auction.close(clock.instant());
        transactionalWriter.update(auction);
    }

    @Override
    public UUID openAuction(OpenAuctionCommand command) {
        boolean carExists = carLookupPort.existsById(command.carId());

        if (!carExists) {
            throw new CarNotFoundException(command.carId());
        }

        Auction auction = Auction.open(
                command.carId(),
                command.sellerId(),
                command.startingPrice(),
                command.endsAt(),
                clock.instant()
        );

        transactionalWriter.create(auction);

        return auction.id();
    }
}