package com.dbwjd.transfer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, String> {

    Optional<Transfer> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);

    // Today's active send amount. Cancelled and failed rows are not included.
    @Query("""
            SELECT SUM(t.sendAmount)
            FROM Transfer t
            WHERE t.customerId = :customerId
              AND t.status <> :cancelled
              AND t.status <> :failed
              AND t.createdAt >= :start
              AND t.createdAt < :end
            """)
    BigDecimal sumActiveSendAmount(@Param("customerId") String customerId,
                                   @Param("cancelled") TransferStatus cancelled,
                                   @Param("failed") TransferStatus failed,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

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

    // One update. A row changes only while it is still REQUESTED.
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Transfer t
            SET t.status = :sending, t.updatedAt = :updatedAt
            WHERE t.transferId = :transferId AND t.status = :requested
            """)
    int markSendingIfRequested(@Param("transferId") String transferId,
                               @Param("sending") TransferStatus sending,
                               @Param("requested") TransferStatus requested,
                               @Param("updatedAt") LocalDateTime updatedAt);

    // One update. A row changes only while it is still SENDING.
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Transfer t
            SET t.status = :failed, t.failureReason = :failureReason, t.updatedAt = :updatedAt
            WHERE t.transferId = :transferId AND t.status = :sending
            """)
    int markFailedIfSending(@Param("transferId") String transferId,
                            @Param("failed") TransferStatus failed,
                            @Param("failureReason") String failureReason,
                            @Param("sending") TransferStatus sending,
                            @Param("updatedAt") LocalDateTime updatedAt);
}
