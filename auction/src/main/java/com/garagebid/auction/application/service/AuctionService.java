package com.garagebid.auction.application.service;

import com.garagebid.auction.application.port.in.CloseAuctionUseCase;
import com.garagebid.auction.application.port.in.PlaceBidUseCase;
import com.garagebid.auction.application.port.out.LoadAuctionPort;
import com.garagebid.auction.application.port.out.SaveAuctionPort;
import com.garagebid.auction.domain.model.Auction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuctionService implements PlaceBidUseCase, CloseAuctionUseCase {

    private final LoadAuctionPort loadAuctionPort;
    private final SaveAuctionPort saveAuctionPort;
    private final Clock clock;

    public AuctionService(LoadAuctionPort loadAuctionPort,
                          SaveAuctionPort saveAuctionPort,
                          Clock clock) {
        this.loadAuctionPort = loadAuctionPort;
        this.saveAuctionPort = saveAuctionPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void placeBid(PlaceBidCommand command) {
        Auction auction = loadAuctionPort.loadById(command.auctionId())
                .orElseThrow(() -> new AuctionNotFoundException(command.auctionId()));

        auction.placeBid(command.bidderId(), command.amount(), Instant.now(clock));

        saveAuctionPort.save(auction);
    }

    @Override
    @Transactional
    public void closeAuction(UUID auctionId) {
        Auction auction = loadAuctionPort.loadById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        auction.close();

        saveAuctionPort.save(auction);
    }
}