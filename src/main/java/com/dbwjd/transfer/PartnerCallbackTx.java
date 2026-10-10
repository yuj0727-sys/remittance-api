package com.dbwjd.transfer;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class PartnerCallbackTx {

    private final TransferRepository transfers;
    private final CallbackEventRepository events;
    private final EntityManager entityManager;
    private final Clock clock;

    public PartnerCallbackTx(TransferRepository transfers,
                             CallbackEventRepository events,
                             EntityManager entityManager,
                             Clock clock) {
        this.transfers = transfers;
        this.events = events;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public CallbackResponse handle(PartnerCallbackRequest request) {
        Transfer transfer = requireTransfer(request.getPartnerRef());
        CallbackResponse alreadyStored = storedEvent(request, transfer);
        if (alreadyStored != null) {
            return alreadyStored;
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        events.saveAndFlush(new CallbackEvent(request.getEventId(), request.getPartnerRef(), now));

        int updated = transfers.completeIfSending(
                transfer.getTransferId(), TransferStatus.COMPLETED, TransferStatus.SENDING, now);
        if (updated == 1) {
            return new CallbackResponse(transfer.getTransferId(), TransferStatus.COMPLETED.name());
        }

        Transfer current = transfers.findById(transfer.getTransferId()).orElseThrow();
        if (!current.getStatus().canTransitionTo(TransferStatus.COMPLETED)) {
            throw new ConflictException("cannot complete transfer in status " + current.getStatus());
        }
        // The update lost. Another request already left SENDING.
        throw new ConflictException("cannot complete transfer in status " + current.getStatus());
    }

    // Runs after the failed insert rolls back, so it can see the winning event row.
    @Transactional(readOnly = true)
    public CallbackResponse replay(PartnerCallbackRequest request) {
        Transfer transfer = requireTransfer(request.getPartnerRef());
        CallbackResponse alreadyStored = storedEvent(request, transfer);
        if (alreadyStored != null) {
            return alreadyStored;
        }
        throw new ConflictException("eventId already used");
    }

    private Transfer requireTransfer(String partnerRef) {
        return transfers.findByPartnerRef(partnerRef)
                .orElseThrow(() -> new NotFoundException("transfer not found"));
    }

    // Same event id and partner ref returns the transfer as it is. A different partner ref is a conflict.
    private CallbackResponse storedEvent(PartnerCallbackRequest request, Transfer transfer) {
        CallbackEvent event = events.findById(request.getEventId()).orElse(null);
        if (event == null) {
            return null;
        }
        if (!event.getPartnerRef().equals(request.getPartnerRef())) {
            throw new ConflictException("eventId already used for another partnerRef");
        }
        // The transfer in memory may still say SENDING. Read the committed row.
        entityManager.refresh(transfer);
        return new CallbackResponse(transfer.getTransferId(), transfer.getStatus().name());
    }
}
