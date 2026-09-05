package com.garagebid.auction.application.service;

import com.garagebid.auction.application.event.AuctionEventMapper;
import com.garagebid.auction.application.event.IntegrationEvent;
import com.garagebid.auction.application.port.out.CreateAuctionPort;
import com.garagebid.auction.application.port.out.SaveAuctionPort;
import com.garagebid.auction.application.port.out.SaveIntegrationEventPort;
import com.garagebid.auction.domain.event.DomainEvent;
import com.garagebid.auction.domain.model.Auction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuctionTransactionalWriter {

    private final CreateAuctionPort createAuctionPort;
    private final SaveAuctionPort saveAuctionPort;
    private final SaveIntegrationEventPort saveIntegrationEventPort;
    private final AuctionEventMapper eventMapper;

    public AuctionTransactionalWriter(
            CreateAuctionPort createAuctionPort,
            SaveAuctionPort saveAuctionPort,
            SaveIntegrationEventPort saveIntegrationEventPort,
            AuctionEventMapper eventMapper
    ) {
        this.createAuctionPort = createAuctionPort;
        this.saveAuctionPort = saveAuctionPort;
        this.saveIntegrationEventPort = saveIntegrationEventPort;
        this.eventMapper = eventMapper;
    }

    @Transactional
    public void create(Auction auction) {
        createAuctionPort.create(auction);
        persistDomainEvents(auction);
    }

    @Transactional
    public void update(Auction auction) {
        saveAuctionPort.save(auction);
        persistDomainEvents(auction);
    }

    private void persistDomainEvents(Auction auction) {
        for (DomainEvent domainEvent : auction.pullDomainEvents()) {
            IntegrationEvent integrationEvent =
                    eventMapper.toIntegrationEvent(domainEvent);

            saveIntegrationEventPort.save(integrationEvent);
        }
    }
}