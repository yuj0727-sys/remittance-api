package com.dbwjd.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

@Entity
@Table(name = "callback_events")
public class CallbackEvent implements Persistable<String> {

    @Id
    @Column(name = "event_id", length = 64, nullable = false)
    private String eventId;

    @Column(name = "partner_ref", length = 64, nullable = false)
    private String partnerRef;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    // The id is set before insert. Without this flag Spring Data would update instead of insert.
    @Transient
    private boolean isNew = true;

    protected CallbackEvent() {
    }

    public CallbackEvent(String eventId, String partnerRef, LocalDateTime receivedAt) {
        this.eventId = eventId;
        this.partnerRef = partnerRef;
        this.receivedAt = receivedAt;
    }

    @Override
    public String getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        isNew = false;
    }

    public String getPartnerRef() {
        return partnerRef;
    }
}
