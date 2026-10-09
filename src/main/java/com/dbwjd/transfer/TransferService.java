package com.dbwjd.transfer;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
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

    public TransferService(CustomerLookup customerLookup,
                           TransferRepository transfers,
                           TransferInserter inserter) {
        this.validator = new TransferRequestValidator(customerLookup);
        this.calculator = new TransferCalculator();
        this.transfers = transfers;
        this.inserter = inserter;
    }

    public TransferResponse create(String idempotencyKey, CreateTransferRequest request) {
        requireIdempotencyKey(idempotencyKey);
        BigDecimal sendAmount = validator.validate(request);
        String requestHash = requestHash(request, sendAmount);

        try {
            Transfer saved = inserter.insert(request, sendAmount, calculator.calculate(sendAmount), idempotencyKey, requestHash);
            return TransferResponse.from(saved);
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
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
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