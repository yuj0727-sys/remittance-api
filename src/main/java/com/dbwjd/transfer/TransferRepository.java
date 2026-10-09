package com.dbwjd.transfer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, String> {

    Optional<Transfer> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);

    // One update. A row changes only while it is still REQUESTED.
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Transfer t
            SET t.status = :cancelled, t.updatedAt = :updatedAt
            WHERE t.transferId = :transferId AND t.status = :requested
            """)
    int cancelIfRequested(@Param("transferId") String transferId,
                          @Param("cancelled") TransferStatus cancelled,
                          @Param("requested") TransferStatus requested,
                          @Param("updatedAt") LocalDateTime updatedAt);
}
