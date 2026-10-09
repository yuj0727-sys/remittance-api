package com.dbwjd.transfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TransferSendService {

    private static final Logger log = LoggerFactory.getLogger(TransferSendService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final TransferStateService state;
    private final PartnerClient partnerClient;
    private final long backoffMs;

    public TransferSendService(TransferStateService state,
                               PartnerClient partnerClient,
                               @Value("${partner.backoff-ms}") long backoffMs) {
        this.state = state;
        this.partnerClient = partnerClient;
        this.backoffMs = backoffMs;
    }

    // No transaction here. The partner call must not hold a database lock.
    public TransferResponse send(String transferId) {
        Transfer transfer = state.require(transferId);
        state.markSending(transferId);
        if (!accepted(transfer)) {
            state.markFailedIfSending(transferId);
        }
        // ACCEPTED leaves the row SENDING. Only a callback may set COMPLETED.
        return TransferResponse.from(state.require(transferId));
    }

    private boolean accepted(Transfer transfer) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            PartnerResult result = partnerClient.call(transfer.getPartnerRef());
            log.info("partner attempt transferId={} attempt={}/3 partnerRef={} result={}",
                    transfer.getTransferId(), attempt, transfer.getPartnerRef(), result.getStatus());
            if (result.getStatus() == PartnerResult.Status.ACCEPTED) {
                return true;
            }
            if (attempt < MAX_ATTEMPTS) {
                sleep(backoffMs * attempt);
            }
        }
        return false;
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("partner backoff interrupted", ex);
        }
    }
}
