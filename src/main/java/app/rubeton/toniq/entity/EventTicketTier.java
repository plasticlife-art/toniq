package app.rubeton.toniq.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@JmixEntity
@Entity
@Table(name = "TONIQ_EVENT_TICKET_TIER", indexes = {
        @Index(name = "IDX_TONIQ_EVENT_TIER_ON_EVENT_MEGATIX", columnList = "EVENT_ID,MEGATIX_TIER_ID", unique = true),
        @Index(name = "IDX_TONIQ_EVENT_TIER_ON_EVENT_DISPLAY_ORDER", columnList = "EVENT_ID,DISPLAY_ORDER")
})
@Getter
@Setter
public class EventTicketTier {

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

    @Column(name = "MEGATIX_TIER_ID", nullable = false, length = 255)
    private String megatixTierId;

    @InstanceName
    @Column(name = "NAME", nullable = false, length = 255)
    private String name;

    @Column(name = "DESCRIPTION_", length = 1000)
    private String description;

    @Column(name = "CURRENCY_CODE", nullable = false, length = 10)
    private String currencyCode;

    @Column(name = "FACE_PRICE", nullable = false, precision = 19, scale = 2)
    private BigDecimal facePrice;

    @Column(name = "AVAILABILITY_COUNT")
    private Integer availabilityCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "AVAILABILITY_STATE", length = 50)
    private TierAvailabilityState availabilityState;

    @Column(name = "SALES_STARTS_AT")
    private OffsetDateTime salesStartsAt;

    @Column(name = "SALES_ENDS_AT")
    private OffsetDateTime salesEndsAt;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive = true;

    @Column(name = "LAST_AVAILABILITY_SYNC_AT")
    private OffsetDateTime lastAvailabilitySyncAt;

    @Lob
    @Column(name = "RAW_PAYLOAD_JSON")
    private String rawPayloadJson;

    @Column(name = "CREATED_AT")
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private OffsetDateTime updatedAt;

}
