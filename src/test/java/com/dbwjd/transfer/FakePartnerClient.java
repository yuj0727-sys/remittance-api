package com.dbwjd.transfer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class FakePartnerClient implements PartnerClient {

    private final ConcurrentHashMap<String, AtomicInteger> callsByRef = new ConcurrentHashMap<>();

    @Override
    public PartnerResult call(String partnerRef) {
        int attempt = callsByRef.computeIfAbsent(partnerRef, key -> new AtomicInteger()).incrementAndGet();
        if ("OK_REF".equals(partnerRef)) {
            return PartnerResult.accepted();
        }
        if ("FAIL_REF".equals(partnerRef)) {
            return PartnerResult.failed("rejected");
        }
        if ("FLAKY_REF".equals(partnerRef)) {
            if (attempt < 3) {
                return PartnerResult.failed("rejected");
            }
            return PartnerResult.accepted();
        }
        throw new IllegalStateException("unexpected partnerRef " + partnerRef);
    }

    int callsFor(String partnerRef) {
        AtomicInteger calls = callsByRef.get(partnerRef);
        return calls == null ? 0 : calls.get();
    }

    void reset() {
        callsByRef.clear();
    }
}
