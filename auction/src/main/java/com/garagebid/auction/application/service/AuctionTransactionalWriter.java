package com.garagebid.auction.application.service;

import com.garagebid.auction.application.event.AuctionEventMapper;
import com.garagebid.auction.application.event.IntegrationEvent;

import com.garagebid.auction.application.port.out.CreateAuctionPort;
import com.garagebid.auction.application.port.out.SaveIntegrationEventPort;
import com.garagebid.auction.domain.event.DomainEvent;
import com.garagebid.auction.domain.model.Auction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuctionTransactionalWriter {

    private final CreateAuctionPort createAuctionPort;
    private final SaveIntegrationEventPort saveIntegrationEventPort;
    private final AuctionEventMapper eventMapper;

    public AuctionTransactionalWriter(
            CreateAuctionPort createAuctionPort,
            SaveIntegrationEventPort saveIntegrationEventPort,
            AuctionEventMapper eventMapper
    ) {
        this.createAuctionPort = createAuctionPort;
        this.saveIntegrationEventPort = saveIntegrationEventPort;
        this.eventMapper = eventMapper;
    }

    @Transactional
    public void save(Auction auction) {
        createAuctionPort.create(auction);

        for (DomainEvent domainEvent : auction.pullDomainEvents()) {
            IntegrationEvent event = eventMapper.toIntegrationEvent(domainEvent);
            saveIntegrationEventPort.save(event);
        }
    }
}