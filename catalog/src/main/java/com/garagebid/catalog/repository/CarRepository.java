package com.garagebid.catalog.repository;

import com.garagebid.catalog.domain.Car;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CarRepository extends JpaRepository<Car, UUID> {
}