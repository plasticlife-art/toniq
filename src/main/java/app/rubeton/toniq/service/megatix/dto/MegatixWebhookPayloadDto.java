package app.rubeton.toniq.service.megatix.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class MegatixWebhookPayloadDto {

    private String event;
    private DataDto data;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    @Setter
    public static class DataDto {

        @com.fasterxml.jackson.annotation.JsonAlias("event_id")
        private String eventId;
        private Boolean enabled;
    }
}
