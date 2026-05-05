package app.rubeton.toniq.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
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
@Table(name = "TONIQ_ORGANISER", indexes = {
        @Index(name = "IDX_TONIQ_ORGANISER_ON_MEGATIX_ORGANISER_ID", columnList = "MEGATIX_ORGANISER_ID", unique = true)
})
@Getter
@Setter
public class Organiser {

    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @Column(name = "MEGATIX_ORGANISER_ID", nullable = false, length = 255)
    private String megatixOrganiserId;

    @InstanceName
    @Column(name = "NAME", length = 255)
    private String name;

    @Column(name = "EMAIL", length = 255)
    private String email;

    @Lob
    @Column(name = "RAW_PAYLOAD_JSON")
    private String rawPayloadJson;

    @Column(name = "CREATED_AT")
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "organiser", fetch = FetchType.LAZY)
    private List<Event> events = new ArrayList<>();

}
