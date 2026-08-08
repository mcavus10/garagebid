package com.garagebid.auction.application.service;

public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(Throwable cause) {
        super("Catalog service is currently unavailable", cause);
    }
}