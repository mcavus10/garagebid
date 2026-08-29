package com.garagebid.auction.adapter.out.persistence;

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
@Table(name = "auctions")
@Getter
@Setter
@NoArgsConstructor
public class AuctionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "car_id", nullable = false)
    private UUID carId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "starting_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal startingAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "highest_bidder_id")
    private UUID highestBidderId;

    @Column(name = "highest_amount", precision = 14, scale = 2)
    private BigDecimal highestAmount;

    @Column(name = "highest_placed_at")
    private Instant highestPlacedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}