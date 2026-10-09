package com.dbwjd.transfer;

public enum TransferStatus {
    REQUESTED,
    SENDING,
    COMPLETED,
    FAILED,
    CANCELLED;

    // Legal moves. Cancel uses only REQUESTED -> CANCELLED.
    public boolean canTransitionTo(TransferStatus next) {
        return switch (this) {
            case REQUESTED -> next == SENDING || next == CANCELLED;
            case SENDING -> next == COMPLETED || next == FAILED;
            case COMPLETED, FAILED, CANCELLED -> false;
        };
    }
}
