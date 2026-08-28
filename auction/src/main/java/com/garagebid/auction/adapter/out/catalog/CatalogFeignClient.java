package com.garagebid.auction.adapter.out.catalog;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "catalog-service",
        path = "/api/v1/cars",
        configuration = CatalogFeignConfiguration.class
)
public interface CatalogFeignClient {

    @GetMapping("/{carId}")
    void getCar(@PathVariable("carId") UUID carId);
}