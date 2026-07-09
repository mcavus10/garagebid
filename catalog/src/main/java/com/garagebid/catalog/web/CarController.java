package com.garagebid.catalog.web;

import com.garagebid.catalog.domain.Car;
import com.garagebid.catalog.service.CarService;
import com.garagebid.catalog.web.dto.CarResponse;
import com.garagebid.catalog.web.dto.CreateCarRequest;
import com.garagebid.catalog.web.mapper.CarMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cars")
public class CarController {

    private final CarService carService;
    private final CarMapper carMapper;

    public CarController(CarService carService, CarMapper carMapper) {
        this.carService = carService;
        this.carMapper = carMapper;
    }

    @GetMapping
    public List<CarResponse> list() {
        return carService.findAll().stream().map(carMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public CarResponse get(@PathVariable UUID id) {
        return carMapper.toResponse(carService.getById(id));
    }

    @PostMapping
    public ResponseEntity<CarResponse> create(@Valid @RequestBody CreateCarRequest request) {
        Car saved = carService.create(carMapper.toEntity(request));
        CarResponse body = carMapper.toResponse(saved);
        return ResponseEntity.created(URI.create("/api/v1/cars/" + body.id())).body(body);
    }
}