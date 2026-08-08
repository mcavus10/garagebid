package com.garagebid.auction.adapter.out.catalog;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.UUID;

@HttpExchange("/api/v1/cars")
interface CatalogHttpClient {

    @GetExchange("/{carId}")
    ResponseEntity<Void> getCar(@PathVariable UUID carId);
}