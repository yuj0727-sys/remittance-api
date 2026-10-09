package com.dbwjd.transfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartnerClientTest {

    private final PartnerClient client = new DeterministicPartnerClient();

    @Test
    void ref1ToRef50ContainsAtLeastOneFailureAndOneSuccess() {
        boolean sawFailure = false;
        boolean sawSuccess = false;

        for (int n = 1; n <= 50; n++) {
            PartnerResult result = client.call("REF-" + n);
            if (result.getStatus() == PartnerResult.Status.FAILED) {
                sawFailure = true;
            }
            if (result.getStatus() == PartnerResult.Status.ACCEPTED) {
                sawSuccess = true;
            }
        }

        assertTrue(sawFailure);
        assertTrue(sawSuccess);
    }

    @Test
    void samePartnerRefReturnsTheSameResultFiveTimes() {
        PartnerResult first = client.call("REF-1");

        for (int n = 0; n < 4; n++) {
            PartnerResult again = client.call("REF-1");
            assertEquals(first.getStatus(), again.getStatus());
            assertEquals(first.getReason(), again.getReason());
        }
    }

    @Test
    void oneCallTakesAtLeast200Milliseconds() {
        long startNanos = System.nanoTime();
        client.call("REF-1");
        long elapsedNanos = System.nanoTime() - startNanos;

        assertTrue(elapsedNanos >= 200_000_000L);
    }
}
