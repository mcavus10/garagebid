package com.garagebid.auction.application.service;

import com.garagebid.auction.application.event.AuctionEventMapper;
import com.garagebid.auction.application.event.IntegrationEvent;
import com.garagebid.auction.application.port.out.SaveAuctionPort;
import com.garagebid.auction.application.port.out.SaveIntegrationEventPort;
import com.garagebid.auction.domain.event.DomainEvent;
import com.garagebid.auction.domain.model.Auction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class AuctionTransactionalWriter {

    private final SaveAuctionPort saveAuctionPort;
    private final SaveIntegrationEventPort saveIntegrationEventPort;
    private final AuctionEventMapper eventMapper;

    public AuctionTransactionalWriter(
            SaveAuctionPort saveAuctionPort,
            SaveIntegrationEventPort saveIntegrationEventPort,
            AuctionEventMapper eventMapper
    ) {
        this.saveAuctionPort = saveAuctionPort;
        this.saveIntegrationEventPort = saveIntegrationEventPort;
        this.eventMapper = eventMapper;
    }

    @Transactional
    public void save(Auction auction) {
        saveAuctionPort.save(auction);
        List<DomainEvent> domainEvents = auction.pullDomainEvents();

        for (DomainEvent domainEvent : domainEvents) {
            IntegrationEvent integrationEvent = eventMapper.toIntegrationEvent(domainEvent);
            saveIntegrationEventPort.save(integrationEvent);
        }
    }

}
