package com.dbwjd.transfer;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
        } catch (DataIntegrityViolationException exception) {
            // UNIQUE (customer_id, idempotency_key) rolled the loser back. Read the winner in a new transaction.
            return replay(request.getCustomerId(), idempotencyKey, requestHash, exception);
        }
    }

    private TransferResponse replay(String customerId,
                                    String idempotencyKey,
                                    String requestHash,
                                    DataIntegrityViolationException exception) {
        Transfer existing = transfers.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)
                .orElseThrow(() -> exception);
        if (existing.getRequestHash().equals(requestHash)) {
            return TransferResponse.from(existing);
        }
        throw new ConflictException("Idempotency-Key was already used with a different request");
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new ValidationException("Idempotency-Key header is required");
        }
    }

    // Parsed sendAmount text, so "500000" and 500000 share one hash.
    private String requestHash(CreateTransferRequest request, BigDecimal sendAmount) {
        String normalized = request.getCustomerId()
                + "|" + request.getSendCurrency()
                + "|" + sendAmount.toPlainString()
                + "|" + request.getReceiveCurrency()
                + "|" + request.getRecipientName();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}