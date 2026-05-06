package app.rubeton.toniq.service.megatix.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class MegatixPromoterDto {

    private String id;
    private String name;
    private String email;
    private MegatixSettlementDetailsDto settlementDetails;

    @JsonAlias({"id", "promoter_id", "organiser_id", "organizer_id"})
    public void setId(final String id) {
        this.id = id;
    }

    @JsonAlias({"name", "organiser_name", "organizer_name"})
    public void setName(final String name) {
        this.name = name;
    }

    public void setEmail(final String email) {
        this.email = email;
    }

    @JsonAlias("settlement_details")
    public void setSettlementDetails(final MegatixSettlementDetailsDto settlementDetails) {
        this.settlementDetails = settlementDetails;
    }
}
