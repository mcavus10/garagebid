package com.garagebid.auction.adapter.out.catalog;

import com.garagebid.auction.application.port.out.CarLookupPort;
import com.garagebid.auction.application.service.CatalogUnavailableException;
import feign.RetryableException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CatalogHttpAdapter implements CarLookupPort {

    private final CatalogFeignClient catalogFeignClient;

    public CatalogHttpAdapter(
            CatalogFeignClient catalogFeignClient
    ) {
        this.catalogFeignClient = catalogFeignClient;
    }

    @Override
    public boolean existsById(UUID carId) {
        try {
            catalogFeignClient.getCar(carId);
            return true;

        } catch (CatalogCarNotFoundException exception) {
            return false;

        } catch (RetryableException exception) {
            throw new CatalogUnavailableException(exception);
        }
    }
}