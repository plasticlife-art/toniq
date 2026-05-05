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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@JmixEntity
@Entity
@Table(name = "TONIQ_EVENT_SYNC_STATE", indexes = {
        @Index(name = "IDX_TONIQ_EVENT_SYNC_STATE_ON_EVENT_ID", columnList = "EVENT_ID", unique = true),
        @Index(name = "IDX_TONIQ_EVENT_SYNC_STATE_ON_EVENT_DATA_SYNC_AT", columnList = "LAST_EVENT_DATA_SYNC_AT"),
        @Index(name = "IDX_TONIQ_EVENT_SYNC_STATE_ON_AVAIL_SYNC_AT", columnList = "LAST_AVAILABILITY_SYNC_AT")
})
@Getter
@Setter
public class EventSyncState {

    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "EVENT_ID", nullable = false, unique = true)
    private Event event;

    @Column(name = "LAST_SYNCED_AT")
    private OffsetDateTime lastSyncedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "LAST_SYNC_RESULT", length = 50)
    private SyncResult lastSyncResult;

    @Lob
    @Column(name = "LAST_SYNC_ERROR")
    private String lastSyncError;

    @Column(name = "LAST_EVENT_DATA_SYNC_AT")
    private OffsetDateTime lastEventDataSyncAt;

    @Column(name = "LAST_AVAILABILITY_SYNC_AT")
    private OffsetDateTime lastAvailabilitySyncAt;

    @Column(name = "CREATED_AT")
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private OffsetDateTime updatedAt;

}
