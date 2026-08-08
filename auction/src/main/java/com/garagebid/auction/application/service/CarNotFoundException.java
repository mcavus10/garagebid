package com.garagebid.auction.application.service;

import java.util.UUID;

public class CarNotFoundException extends RuntimeException {

    public CarNotFoundException(UUID carId) {
        super("Car not found: " + carId);
    }
}