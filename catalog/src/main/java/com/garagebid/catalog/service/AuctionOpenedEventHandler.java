package com.garagebid.catalog.service;

import com.garagebid.catalog.messaging.AuctionOpenedMessageV1;
import com.garagebid.catalog.repository.CarAuctionProjectionRepository;
import com.garagebid.catalog.repository.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuctionOpenedEventHandler {

    private static final String CONSUMER_NAME = "catalog-auction-projection-v1";
    private static final String EVENT_TYPE = "auction.opened";

    private final ProcessedEventRepository processedEventRepository;
    private final CarAuctionProjectionRepository projectionRepository;

    public AuctionOpenedEventHandler(
            ProcessedEventRepository processedEventRepository,
            CarAuctionProjectionRepository projectionRepository
    ) {
        this.processedEventRepository = processedEventRepository;
        this.projectionRepository = projectionRepository;
    }

    @Transactional
    public void handle(AuctionOpenedMessageV1 event) {
        boolean firstProcessing = processedEventRepository.tryMarkProcessed(CONSUMER_NAME, event.eventId(), EVENT_TYPE);

        if (!firstProcessing) {
            return;
        }

        projectionRepository.upsert(event);
    }
}