package com.dbwjd.transfer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class TransferInserter {

    private final TransferRepository transfers;
    private final CustomerRepository customers;
    private final Clock clock;

    public TransferInserter(TransferRepository transfers, CustomerRepository customers, Clock clock) {
        this.transfers = transfers;
        this.customers = customers;
        this.clock = clock;
    }

    // Own transaction. A duplicate key rolls this back before the caller looks up the winner.
    // Inside the lock: replay the same key, then the daily limit, then insert.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransferResponse create(CreateTransferRequest request,
                                   BigDecimal sendAmount,
                                   TransferCalculator.Result amounts,
                                   String idempotencyKey,
                                   String requestHash) {
        customers.lockByCustomerId(request.getCustomerId())
                .orElseThrow(() -> new ValidationException("customerId does not exist"));

        Transfer existing = transfers.findByCustomerIdAndIdempotencyKey(request.getCustomerId(), idempotencyKey)
                .orElse(null);
        if (existing != null) {
            // A retry returns the original transfer and does not check the daily limit again.
            return sameRequestOrConflict(existing, requestHash);
        }

        if (exceedsDailyLimit(request.getCustomerId(), sendAmount)) {
            throw new LimitExceededException(DailyLimit.EXCEEDED_MESSAGE);
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
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
        return TransferResponse.from(transfers.saveAndFlush(transfer));
    }

    private TransferResponse sameRequestOrConflict(Transfer existing, String requestHash) {
        if (existing.getRequestHash().equals(requestHash)) {
            return TransferResponse.from(existing);
        }
        throw new ConflictException("Idempotency-Key was already used with a different request");
    }

    // Seoul midnight and the next midnight, as UTC instants, compared with created_at.
    private boolean exceedsDailyLimit(String customerId, BigDecimal sendAmount) {
        LocalDate seoulDay = LocalDate.ofInstant(clock.instant(), DailyLimit.ZONE);
        Instant startInstant = seoulDay.atStartOfDay(DailyLimit.ZONE).toInstant();
        Instant endInstant = seoulDay.plusDays(1).atStartOfDay(DailyLimit.ZONE).toInstant();
        LocalDateTime start = LocalDateTime.ofInstant(startInstant, ZoneOffset.UTC);
        LocalDateTime end = LocalDateTime.ofInstant(endInstant, ZoneOffset.UTC);

        BigDecimal used = transfers.sumActiveSendAmount(
                customerId, TransferStatus.CANCELLED, TransferStatus.FAILED, start, end);
        if (used == null) {
            used = BigDecimal.ZERO;
        }
        return used.add(sendAmount).compareTo(DailyLimit.MAX_SEND_AMOUNT) > 0;
    }
}
