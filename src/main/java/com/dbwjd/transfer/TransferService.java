package com.dbwjd.transfer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class TransferService {

    private final TransferRequestValidator validator;
    private final TransferCalculator calculator;
    private final TransferRepository transfers;

    public TransferService(CustomerLookup customerLookup, TransferRepository transfers) {
        this.validator = new TransferRequestValidator(customerLookup);
        this.calculator = new TransferCalculator();
        this.transfers = transfers;
    }

    @Transactional
    public TransferResponse create(CreateTransferRequest request) {
        BigDecimal sendAmount = validator.validate(request);
        TransferCalculator.Result amounts = calculator.calculate(sendAmount);
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);

        // idempotency_key and request_hash are NOT NULL. Real values come in the next step.
        Transfer transfer = new Transfer(
                UUID.randomUUID().toString(),
                request.getCustomerId(),
                UUID.randomUUID().toString(),
                "n/a",
                request.getSendCurrency(),
                request.getReceiveCurrency(),
                sendAmount,
                amounts.getFee(),
                amounts.getTotalDebit(),
                amounts.getReceiveAmount(),
                request.getRecipientName(),
                TransferStatus.REQUESTED,
                now,
                now);

        return TransferResponse.from(transfers.save(transfer));
    }
}
