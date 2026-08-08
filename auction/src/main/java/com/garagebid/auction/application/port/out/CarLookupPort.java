package com.garagebid.auction.application.port.out;

import java.util.UUID;

public interface CarLookupPort {

    boolean existsById(UUID carId);
}
