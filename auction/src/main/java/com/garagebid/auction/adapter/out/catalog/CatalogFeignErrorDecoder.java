package com.garagebid.auction.adapter.out.catalog;

import com.garagebid.auction.application.service.CatalogUnavailableException;
import feign.FeignException;
import feign.Response;
import feign.codec.ErrorDecoder;

final class CatalogFeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder =
            new ErrorDecoder.Default();

    @Override
    public Exception decode(
            String methodKey,
            Response response
    ) {
        if (response.status() == 404) {
            return new CatalogCarNotFoundException();
        }

        if (response.status() >= 500) {
            return new CatalogUnavailableException(
                    FeignException.errorStatus(
                            methodKey,
                            response
                    )
            );
        }

        return defaultDecoder.decode(
                methodKey,
                response
        );
    }
}