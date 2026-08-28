package com.garagebid.auction.application.port.out;

import com.garagebid.auction.application.event.IntegrationEvent;

public interface SaveIntegrationEventPort {

    void save(IntegrationEvent event);
}