package com.dbwjd.transfer;

import org.junit.jupiter.api.Test;

import static com.dbwjd.transfer.TransferStatus.CANCELLED;
import static com.dbwjd.transfer.TransferStatus.COMPLETED;
import static com.dbwjd.transfer.TransferStatus.FAILED;
import static com.dbwjd.transfer.TransferStatus.REQUESTED;
import static com.dbwjd.transfer.TransferStatus.SENDING;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferStatusTest {

    @Test
    void allowsOnlyTheFourMoves() {
        assertTrue(REQUESTED.canTransitionTo(SENDING));
        assertTrue(REQUESTED.canTransitionTo(CANCELLED));
        assertTrue(SENDING.canTransitionTo(COMPLETED));
        assertTrue(SENDING.canTransitionTo(FAILED));

        assertFalse(REQUESTED.canTransitionTo(COMPLETED));
        assertFalse(REQUESTED.canTransitionTo(FAILED));
        assertFalse(SENDING.canTransitionTo(CANCELLED));
        assertFalse(COMPLETED.canTransitionTo(FAILED));
        assertFalse(FAILED.canTransitionTo(SENDING));
        assertFalse(CANCELLED.canTransitionTo(REQUESTED));
    }
}
