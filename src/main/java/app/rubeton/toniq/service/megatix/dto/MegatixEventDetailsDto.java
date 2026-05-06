package app.rubeton.toniq.service.megatix.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class MegatixEventDetailsDto {

    private String id;
    private String promoterId;
    private String title;
    private String slug;
    private String description;
    private String startAt;
    private String endAt;
    private String timezone;
    private String currencyCode;
    private String venueName;
    private String cover;
    private JsonNode venue;
    private JsonNode photos;
    private List<MegatixTicketDto> tickets = new ArrayList<>();

    public void setId(final String id) {
        this.id = id;
    }

    @JsonAlias({"promoter_id", "organiser_id", "organizer_id"})
    public void setPromoterId(final String promoterId) {
        this.promoterId = promoterId;
    }

    @JsonAlias({"title", "name"})
    public void setTitle(final String title) {
        this.title = title;
    }

    public void setSlug(final String slug) {
        this.slug = slug;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    @JsonAlias({"start_at", "start_date", "event_start_at", "start_datetime", "on_sale_datetime"})
    public void setStartAt(final String startAt) {
        this.startAt = startAt;
    }

    @JsonAlias({"end_at", "end_date", "event_end_at", "end_datetime"})
    public void setEndAt(final String endAt) {
        this.endAt = endAt;
    }

    @JsonAlias({"timezone", "timezone_name"})
    public void setTimezone(final String timezone) {
        this.timezone = timezone;
    }

    @JsonAlias({"currency_code_iso4217", "currency_code", "currency"})
    public void setCurrencyCode(final String currencyCode) {
        this.currencyCode = currencyCode;
    }

    @JsonAlias({"venue_name", "venue_display_name"})
    public void setVenueName(final String venueName) {
        this.venueName = venueName;
    }

    public void setCover(final String cover) {
        this.cover = cover;
    }

    public void setVenue(final JsonNode venue) {
        this.venue = venue;
    }

    @JsonAlias({"photos", "gallery"})
    public void setPhotos(final JsonNode photos) {
        this.photos = photos;
    }

    public void setTickets(final List<MegatixTicketDto> tickets) {
        this.tickets = tickets != null ? tickets : new ArrayList<>();
    }
}
