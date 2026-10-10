package com.dbwjd.transfer;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class PartnerCallbackService {

    private static final int MAX_EVENT_ID_LENGTH = 64;

    private final PartnerCallbackTx callbackTx;

    public PartnerCallbackService(PartnerCallbackTx callbackTx) {
        this.callbackTx = callbackTx;
    }

    // Validate first. A duplicate event id is retried after that transaction rolls back.
    public CallbackResponse receive(PartnerCallbackRequest request) {
        validate(request);
        try {
            return callbackTx.handle(request);
        } catch (DataIntegrityViolationException ignored) {
            return callbackTx.replay(request);
        }
    }

    private void validate(PartnerCallbackRequest request) {
        requireText(request.getPartnerRef(), "partnerRef");
        requireText(request.getEventId(), "eventId");
        requireText(request.getStatus(), "status");
        if (request.getEventId().length() > MAX_EVENT_ID_LENGTH) {
            throw new ValidationException("eventId must be at most 64 characters");
        }
        // This assignment's partner sends only COMPLETED.
        if (!"COMPLETED".equals(request.getStatus())) {
            throw new ValidationException("status must be COMPLETED");
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " is required");
        }
    }
}
