package com.garagebid.catalog.service;

import java.util.UUID;

public class CarNotFoundException extends RuntimeException {
    public CarNotFoundException(UUID id) {
        super("Car not found: " + id);
    }
}