package app.rubeton.toniq.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "TONIQ_ORGANISER_SETTLEMENT", indexes = {
        @Index(name = "IDX_TONIQ_ORG_SETTLEMENT_ON_ORGANISER_ID", columnList = "ORGANISER_ID", unique = true)
})
@Getter
@Setter
public class OrganiserSettlementDetails {

    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ORGANISER_ID", nullable = false, unique = true)
    private Organiser organiser;

    @Column(name = "ACCOUNT_NUMBER", length = 255)
    private String accountNumber;

    @Column(name = "ACCOUNT_BSB", length = 255)
    private String accountBsb;

    @Column(name = "ACCOUNT_NAME", length = 500)
    private String accountName;

    @Column(name = "COUNTRY_CODE", length = 16)
    private String countryCode;

    @Column(name = "WALLET_ADDRESS", length = 255)
    private String walletAddress;

    @Lob
    @Column(name = "RAW_PAYLOAD_JSON")
    private String rawPayloadJson;

    @Column(name = "CREATED_AT")
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private OffsetDateTime updatedAt;
}
