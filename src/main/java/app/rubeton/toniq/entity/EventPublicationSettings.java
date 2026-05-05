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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@JmixEntity
@Entity
@Table(name = "TONIQ_EVENT_PUBLICATION", indexes = {
        @Index(name = "IDX_TONIQ_EVENT_PUBLICATION_ON_EVENT_ID", columnList = "EVENT_ID", unique = true),
        @Index(name = "IDX_TONIQ_EVENT_PUBLICATION_ON_CRYPTO_PUBLISHED", columnList = "CRYPTO_ENABLED,PUBLISHED"),
        @Index(name = "IDX_TONIQ_EVENT_PUBLICATION_ON_STATE", columnList = "PUBLICATION_STATE")
})
@Getter
@Setter
public class EventPublicationSettings {

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

    @Column(name = "CRYPTO_ENABLED", nullable = false)
    private Boolean cryptoEnabled = false;

    @Column(name = "PUBLISHED", nullable = false)
    private Boolean published = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "PUBLICATION_STATE", nullable = false, length = 50)
    private PublicationState publicationState = PublicationState.DRAFT;

    @Column(name = "PUBLICATION_REASON", length = 255)
    private String publicationReason;

    @Column(name = "FIRST_PUBLISHED_AT")
    private OffsetDateTime firstPublishedAt;

    @Column(name = "LAST_PUBLISHED_AT")
    private OffsetDateTime lastPublishedAt;

    @Column(name = "LAST_UNPUBLISHED_AT")
    private OffsetDateTime lastUnpublishedAt;

    @Column(name = "CREATED_AT")
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private OffsetDateTime updatedAt;

}
