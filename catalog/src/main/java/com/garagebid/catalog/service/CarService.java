package com.garagebid.catalog.service;

import com.garagebid.catalog.domain.Car;
import com.garagebid.catalog.repository.CarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class CarService {

    private final CarRepository carRepository;

    public CarService(CarRepository carRepository) {
        this.carRepository = carRepository;
    }

    @Transactional(readOnly = true)
    public List<Car> findAll() {
        return carRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Car getById(UUID id) {
        return carRepository.findById(id).orElseThrow(() -> new CarNotFoundException(id));
    }

    @Transactional
    public Car create(Car car) {
        return carRepository.save(car);
    }
}