package com.dbwjd.transfer;

import org.springframework.stereotype.Component;

@Component
public class DeterministicPartnerClient implements PartnerClient {

    private static final long CALL_DELAY_MILLIS = 200;

    private static final String FAILURE_REASON = "rejected";

    // ACCEPTED means the partner got the request, NOT that money arrived.
    @Override
    public PartnerResult call(String partnerRef) {
        sleep();
        if (Math.abs(partnerRef.hashCode()) % 10 < 3) {
            return PartnerResult.failed(FAILURE_REASON);
        }
        return PartnerResult.accepted();
    }

    private void sleep() {
        try {
            Thread.sleep(CALL_DELAY_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("partner call interrupted", ex);
        }
    }
}
