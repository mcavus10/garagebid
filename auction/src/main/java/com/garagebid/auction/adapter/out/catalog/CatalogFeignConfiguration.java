package com.garagebid.auction.adapter.out.catalog;

import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class CatalogFeignConfiguration {

    @Bean
    ErrorDecoder catalogFeignErrorDecoder() {
        return new CatalogFeignErrorDecoder();
    }
}