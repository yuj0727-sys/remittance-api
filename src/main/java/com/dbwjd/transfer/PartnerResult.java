package com.dbwjd.transfer;

public final class PartnerResult {

    public enum Status {
        ACCEPTED,
        FAILED
    }

    private final Status status;
    private final String reason;

    private PartnerResult(Status status, String reason) {
        this.status = status;
        this.reason = reason;
    }

    public static PartnerResult accepted() {
        return new PartnerResult(Status.ACCEPTED, null);
    }

    public static PartnerResult failed(String reason) {
        return new PartnerResult(Status.FAILED, reason);
    }

    public Status getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }
}
