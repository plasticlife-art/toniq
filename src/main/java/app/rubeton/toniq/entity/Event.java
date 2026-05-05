package app.rubeton.toniq.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JmixEntity
@Entity
@Table(name = "TONIQ_EVENT", indexes = {
        @Index(name = "IDX_TONIQ_EVENT_ON_MEGATIX_EVENT_ID", columnList = "MEGATIX_EVENT_ID", unique = true),
        @Index(name = "IDX_TONIQ_EVENT_ON_ORGANISER_ID", columnList = "ORGANISER_ID"),
        @Index(name = "IDX_TONIQ_EVENT_ON_DELETED_AT", columnList = "DELETED_AT")
})
@Getter
@Setter
public class Event {

    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @Column(name = "MEGATIX_EVENT_ID", nullable = false, length = 255)
    private String megatixEventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ORGANISER_ID", nullable = false)
    private Organiser organiser;

    @InstanceName
    @Column(name = "TITLE", nullable = false, length = 500)
    private String title;

    @Column(name = "SLUG", length = 255)
    private String slug;

    @Lob
    @Column(name = "DESCRIPTION_")
    private String description;

    @Column(name = "VENUE_NAME", length = 255)
    private String venueName;

    @Lob
    @Column(name = "VENUE_JSON")
    private String venueJson;

    @Lob
    @Column(name = "PHOTOS_JSON")
    private String photosJson;

    @Column(name = "EVENT_START_AT")
    private OffsetDateTime eventStartAt;

    @Column(name = "EVENT_END_AT")
    private OffsetDateTime eventEndAt;

    @Column(name = "TIMEZONE_NAME", length = 255)
    private String timezoneName;

    @Lob
    @Column(name = "RAW_PAYLOAD_JSON")
    private String rawPayloadJson;

    @Column(name = "DELETED_AT")
    private OffsetDateTime deletedAt;

    @Column(name = "DELETED_BY", length = 255)
    private String deletedBy;

    @Column(name = "CREATED_AT")
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private OffsetDateTime updatedAt;

    @OneToOne(mappedBy = "event", fetch = FetchType.LAZY)
    private EventPublicationSettings publicationSettings;

    @OneToOne(mappedBy = "event", fetch = FetchType.LAZY)
    private EventSyncState syncState;

    @OneToMany(mappedBy = "event", fetch = FetchType.LAZY)
    private List<EventTicketTier> ticketTiers = new ArrayList<>();

    @OneToMany(mappedBy = "event", fetch = FetchType.LAZY)
    private List<EventStatusOverride> statusOverrides = new ArrayList<>();

    @OneToMany(mappedBy = "event", fetch = FetchType.LAZY)
    private List<EventSyncLog> syncLogs = new ArrayList<>();

}
