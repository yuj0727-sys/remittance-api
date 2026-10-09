package com.dbwjd.transfer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, String> {

    Optional<Transfer> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);
}
