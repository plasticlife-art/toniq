package app.rubeton.toniq.service.megatix.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImportedSettlementDetailsData {

    private String accountNumber;
    private String accountBsb;
    private String accountName;
    private String countryCode;
    private String walletAddress;
    private String rawPayloadJson;
}
