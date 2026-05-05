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
@Table(name = "TONIQ_EVENT_SYNC_LOG", indexes = {
        @Index(name = "IDX_TONIQ_EVENT_SYNC_LOG_ON_EVENT_CREATED_AT", columnList = "EVENT_ID,CREATED_AT"),
        @Index(name = "IDX_TONIQ_EVENT_SYNC_LOG_ON_MEGATIX_CREATED_AT", columnList = "MEGATIX_EVENT_ID,CREATED_AT"),
        @Index(name = "IDX_TONIQ_EVENT_SYNC_LOG_ON_TRIGGER_CREATED_AT", columnList = "TRIGGER_SOURCE,CREATED_AT")
})
@Getter
@Setter
public class EventSyncLog {

    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EVENT_ID")
    private Event event;

    @Column(name = "MEGATIX_EVENT_ID", nullable = false, length = 255)
    private String megatixEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TRIGGER_SOURCE", nullable = false, length = 50)
    private SyncLogTriggerSource triggerSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "SYNC_SCOPE", nullable = false, length = 50)
    private SyncLogScope syncScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 50)
    private SyncLogStatus status;

    @Lob
    @Column(name = "REQUEST_PAYLOAD_JSON")
    private String requestPayloadJson;

    @Lob
    @Column(name = "RESPONSE_PAYLOAD_JSON")
    private String responsePayloadJson;

    @Column(name = "ERROR_CODE", length = 255)
    private String errorCode;

    @Lob
    @Column(name = "ERROR_MESSAGE")
    private String errorMessage;

    @Column(name = "STARTED_AT", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "FINISHED_AT")
    private OffsetDateTime finishedAt;

    @Column(name = "CREATED_AT")
    private OffsetDateTime createdAt;

}
