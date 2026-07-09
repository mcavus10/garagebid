package com.garagebid.catalog.web.mapper;

import com.garagebid.catalog.domain.Car;
import com.garagebid.catalog.web.dto.CarResponse;
import com.garagebid.catalog.web.dto.CreateCarRequest;
import org.springframework.stereotype.Component;

@Component
public class CarMapper {

    public Car toEntity(CreateCarRequest r) {
        Car c = new Car();
        c.setMake(r.make());
        c.setModel(r.model());
        c.setModelYear(r.modelYear());
        c.setMileageKm(r.mileageKm());
        c.setPriceUsd(r.priceUsd());
        c.setColor(r.color());
        c.setCondition(r.condition());
        c.setDescription(r.description());
        c.setImageUrl(r.imageUrl());
        return c;
    }

    public CarResponse toResponse(Car c) {
        return new CarResponse(c.getId(), c.getMake(), c.getModel(), c.getModelYear(),
                c.getMileageKm(), c.getPriceUsd(), c.getColor(), c.getCondition(),
                c.getDescription(), c.getImageUrl(), c.getCreatedAt());
    }
}