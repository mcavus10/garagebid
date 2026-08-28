package com.garagebid.auction.adapter.out.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, UUID> {
}