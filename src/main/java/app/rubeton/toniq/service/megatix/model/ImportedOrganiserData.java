package app.rubeton.toniq.service.megatix.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImportedOrganiserData {

    private String megatixOrganiserId;
    private String name;
    private String email;
    private ImportedSettlementDetailsData settlementDetails;
    private String rawPayloadJson;

}
