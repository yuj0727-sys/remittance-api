package com.dbwjd.transfer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class TransferInserter {

    private final TransferRepository transfers;

    public TransferInserter(TransferRepository transfers) {
        this.transfers = transfers;
    }

    // Own transaction. A duplicate key rolls this back before the caller looks up the winner.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transfer insert(CreateTransferRequest request,
                           BigDecimal sendAmount,
                           TransferCalculator.Result amounts,
                           String idempotencyKey,
                           String requestHash) {
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        // Fills a missing partnerRef. This value is not part of the request hash.
        String partnerRef = request.getPartnerRef();
        if (partnerRef == null) {
            partnerRef = "PR-" + UUID.randomUUID();
        }
        Transfer transfer = new Transfer(
                UUID.randomUUID().toString(),
                request.getCustomerId(),
                idempotencyKey,
                requestHash,
                request.getSendCurrency(),
                request.getReceiveCurrency(),
                sendAmount,
                amounts.getFee(),
                amounts.getTotalDebit(),
                amounts.getReceiveAmount(),
                request.getRecipientName(),
                TransferStatus.REQUESTED,
                now,
                now,
                partnerRef,
                null);
        // Flush here so the unique key fails inside this transaction, not later.
        return transfers.saveAndFlush(transfer);
    }
}