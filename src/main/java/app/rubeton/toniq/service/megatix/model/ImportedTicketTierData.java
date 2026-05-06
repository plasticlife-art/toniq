package app.rubeton.toniq.service.megatix.model;

import app.rubeton.toniq.entity.TierAvailabilityState;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
public class ImportedTicketTierData {

    private String megatixTierId;
    private String name;
    private String description;
    private String currencyCode;
    private BigDecimal facePrice;
    private Integer availabilityCount;
    private TierAvailabilityState availabilityState;
    private OffsetDateTime salesStartsAt;
    private OffsetDateTime salesEndsAt;
    private Integer displayOrder;
    private Boolean active;
    private String rawPayloadJson;

}
