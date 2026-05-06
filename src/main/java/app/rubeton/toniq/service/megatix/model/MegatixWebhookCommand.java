package app.rubeton.toniq.service.megatix.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MegatixWebhookCommand {

    private String eventType;
    private String megatixEventId;
    private Boolean cryptoEnabled;
    private String rawPayloadJson;

}
