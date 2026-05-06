package app.rubeton.toniq.service.megatix.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class MegatixSettlementDetailsDto {

    private String accountNumber;
    private String accountBsb;
    private String accountName;
    private String countryCode;
    private String walletAddress;

    @JsonAlias("account_number")
    public void setAccountNumber(final String accountNumber) {
        this.accountNumber = accountNumber;
    }

    @JsonAlias("account_bsb")
    public void setAccountBsb(final String accountBsb) {
        this.accountBsb = accountBsb;
    }

    @JsonAlias("account_name")
    public void setAccountName(final String accountName) {
        this.accountName = accountName;
    }

    @JsonAlias("country_code")
    public void setCountryCode(final String countryCode) {
        this.countryCode = countryCode;
    }

    @JsonAlias("wallet_address")
    public void setWalletAddress(final String walletAddress) {
        this.walletAddress = walletAddress;
    }
}
