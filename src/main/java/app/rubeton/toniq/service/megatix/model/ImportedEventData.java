package app.rubeton.toniq.service.megatix.model;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class ImportedEventData {

    private String megatixEventId;
    private String title;
    private String slug;
    private String description;
    private String venueName;
    private String venueJson;
    private String photosJson;
    private OffsetDateTime eventStartAt;
    private OffsetDateTime eventEndAt;
    private String timezoneName;
    private String rawPayloadJson;

}
