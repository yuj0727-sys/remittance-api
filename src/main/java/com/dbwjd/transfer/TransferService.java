package com.dbwjd.transfer;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

@Service
public class TransferService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    private final TransferRequestValidator validator;
    private final TransferCalculator calculator;
    private final TransferRepository transfers;
    private final TransferInserter inserter;
    private final Clock clock;

    public TransferService(CustomerLookup customerLookup,
                           TransferRepository transfers,
                           TransferInserter inserter,
                           Clock clock) {
        this.validator = new TransferRequestValidator(customerLookup);
        this.calculator = new TransferCalculator();
        this.transfers = transfers;
        this.inserter = inserter;
        this.clock = clock;
    }

    // 1. Validate the body (400).
    // 2. Check the Idempotency-Key header.
    // 3. Lock the customer row.
    // 4. Replay the same key, or reject a different body (409). A replay skips the limit.
    // 5. Enforce the daily limit (422), then insert.
    public TransferResponse create(String idempotencyKey, CreateTransferRequest request) {
        BigDecimal sendAmount = validator.validate(request);
        requireIdempotencyKey(idempotencyKey);
        String requestHash = requestHash(request, sendAmount);

        try {
            return inserter.create(
                    request, sendAmount, calculator.calculate(sendAmount), idempotencyKey, requestHash);
        } catch (DataIntegrityViolationException ignored) {
            // The insert rolled back. Re-read to see which unique key lost.
            return replay(request.getCustomerId(), idempotencyKey, requestHash);
        }
    }

    private TransferResponse replay(String customerId, String idempotencyKey, String requestHash) {
        Transfer existing = transfers.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)
                .orElseThrow(() -> new ConflictException("partnerRef already used"));
        if (existing.getRequestHash().equals(requestHash)) {
            return TransferResponse.from(existing);
        }
        throw new ConflictException("Idempotency-Key was already used with a different request");
    }

    public TransferResponse get(String transferId) {
        return transfers.findById(transferId)
                .map(TransferResponse::from)
                .orElseThrow(() -> new NotFoundException("transfer not found"));
    }

    @Transactional
    public TransferResponse cancel(String transferId) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        int updated = transfers.cancelIfRequested(
                transferId, TransferStatus.CANCELLED, TransferStatus.REQUESTED, now);
        if (updated == 1) {
            return transfers.findById(transferId).map(TransferResponse::from).orElseThrow();
        }

        Transfer existing = transfers.findById(transferId)
                .orElseThrow(() -> new NotFoundException("transfer not found"));
        if (!existing.getStatus().canTransitionTo(TransferStatus.CANCELLED)) {
            throw new ConflictException("cannot cancel transfer in status " + existing.getStatus());
        }
        // The update lost. Another request already left REQUESTED.
        throw new ConflictException("cannot cancel transfer in status " + existing.getStatus());
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new ValidationException("Idempotency-Key header is required");
        }
    }

    // Parsed sendAmount text, so "500000" and 500000 share one hash.
    // partnerRef is only the client value. Missing means empty. Never hash a generated id.
    private String requestHash(CreateTransferRequest request, BigDecimal sendAmount) {
        String partnerRef = request.getPartnerRef() == null ? "" : request.getPartnerRef();
        String normalized = request.getCustomerId()
                + "|" + request.getSendCurrency()
                + "|" + sendAmount.toPlainString()
                + "|" + request.getReceiveCurrency()
                + "|" + request.getRecipientName()
                + "|" + partnerRef;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}