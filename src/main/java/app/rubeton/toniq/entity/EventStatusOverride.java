package app.rubeton.toniq.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@JmixEntity
@Entity
@Table(name = "TONIQ_EVENT_STATUS_OVERRIDE", indexes = {
        @Index(name = "IDX_TONIQ_EVENT_STATUS_OVERRIDE_ON_EVENT_ACTIVE_CREATED", columnList = "EVENT_ID,IS_ACTIVE,CREATED_AT")
})
@Getter
@Setter
public class EventStatusOverride {

    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "EVENT_ID", nullable = false)
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(name = "OVERRIDE_STATUS", nullable = false, length = 50)
    private OverrideStatus overrideStatus;

    @Column(name = "RESCHEDULED_EVENT_START_AT")
    private OffsetDateTime rescheduledEventStartAt;

    @Column(name = "RESCHEDULED_EVENT_END_AT")
    private OffsetDateTime rescheduledEventEndAt;

    @Lob
    @Column(name = "ADMIN_NOTE")
    private String adminNote;

    @Column(name = "ACTOR_IDENTIFIER", nullable = false, length = 255)
    private String actorIdentifier;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive = true;

    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "CLEARED_AT")
    private OffsetDateTime clearedAt;

    @Column(name = "CLEARED_BY", length = 255)
    private String clearedBy;

}
