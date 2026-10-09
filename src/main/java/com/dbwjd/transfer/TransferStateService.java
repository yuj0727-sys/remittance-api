package com.dbwjd.transfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class TransferStateService {

    static final String PARTNER_FAILED_REASON = "partner failed 3 times";

    private static final Logger log = LoggerFactory.getLogger(TransferStateService.class);

    private final TransferRepository transfers;
    private final Clock clock;

    public TransferStateService(TransferRepository transfers, Clock clock) {
        this.transfers = transfers;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Transfer require(String transferId) {
        return transfers.findById(transferId)
                .orElseThrow(() -> new NotFoundException("transfer not found"));
    }

    // Commits on return, before the caller talks to the partner.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSending(String transferId) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        int updated = transfers.markSendingIfRequested(
                transferId, TransferStatus.SENDING, TransferStatus.REQUESTED, now);
        if (updated == 1) {
            return;
        }

        Transfer existing = require(transferId);
        if (!existing.getStatus().canTransitionTo(TransferStatus.SENDING)) {
            throw new ConflictException("cannot send transfer in status " + existing.getStatus());
        }
        // The update lost. Another request already left REQUESTED.
        throw new ConflictException("cannot send transfer in status " + existing.getStatus());
    }

    // Writes FAILED only while the row is still SENDING. A lost update is left as it is.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedIfSending(String transferId) {
        Transfer existing = require(transferId);
        if (!existing.getStatus().canTransitionTo(TransferStatus.FAILED)) {
            log.info("partner failure not saved transferId={} status={}", transferId, existing.getStatus());
            return;
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        int updated = transfers.markFailedIfSending(
                transferId,
                TransferStatus.FAILED,
                PARTNER_FAILED_REASON,
                TransferStatus.SENDING,
                now);
        if (updated == 0) {
            log.info("partner failure not saved transferId={} status changed", transferId);
        }
    }
}
