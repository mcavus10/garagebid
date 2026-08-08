package com.garagebid.auction.adapter.out.catalog;

import com.garagebid.auction.application.port.out.CarLookupPort;
import com.garagebid.auction.application.service.CatalogUnavailableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class CatalogHttpAdapter implements CarLookupPort {

    private final CatalogHttpClient catalogHttpClient;

    public CatalogHttpAdapter(CatalogHttpClient catalogHttpClient) {
        this.catalogHttpClient = catalogHttpClient;
    }

    @Override
    public boolean existsById(UUID carId) {
        try {
            catalogHttpClient.getCar(carId);
            return true;
        } catch (HttpClientErrorException.NotFound exception) {
            return false;
        } catch (RestClientException exception) {
            throw new CatalogUnavailableException(exception);
        }
    }
}