package app.rubeton.toniq.service.megatix.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class MegatixLoginResponseDto {

    @JsonAlias("token_type")
    private String tokenType;

    @JsonAlias("access_token")
    private String accessToken;

    @JsonAlias("expires_in")
    private Long expiresIn;
}
