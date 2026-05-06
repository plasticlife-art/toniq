package app.rubeton.toniq.service.megatix.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class MegatixApiEnvelopeDto {

    private JsonNode data;
    private JsonNode meta;
}
