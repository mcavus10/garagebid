package com.garagebid.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cars")
@Getter
@Setter
@NoArgsConstructor
public class Car {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String make;
    private String model;
    private int modelYear;
    private int mileageKm;

    @Column(precision = 12, scale = 2)
    private BigDecimal priceUsd;

    private String color;

    @Enumerated(EnumType.STRING)
    private CarCondition condition;

    private String description;
    private String imageUrl;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}