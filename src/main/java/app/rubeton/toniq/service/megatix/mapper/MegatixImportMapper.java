package app.rubeton.toniq.service.megatix.mapper;

import app.rubeton.toniq.entity.TierAvailabilityState;
import app.rubeton.toniq.service.megatix.dto.MegatixEventDetailsDto;
import app.rubeton.toniq.service.megatix.dto.MegatixPromoterDto;
import app.rubeton.toniq.service.megatix.dto.MegatixSettlementDetailsDto;
import app.rubeton.toniq.service.megatix.dto.MegatixTicketDto;
import app.rubeton.toniq.service.megatix.model.ImportedEventData;
import app.rubeton.toniq.service.megatix.model.ImportedOrganiserData;
import app.rubeton.toniq.service.megatix.model.ImportedSettlementDetailsData;
import app.rubeton.toniq.service.megatix.model.ImportedTicketTierData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Mapper(componentModel = "spring")
public interface MegatixImportMapper {

    @Mapping(target = "megatixEventId", source = "source.id")
    @Mapping(target = "eventStartAt", source = "source", qualifiedByName = "deriveEventStartAt")
    @Mapping(target = "eventEndAt", source = "source", qualifiedByName = "deriveEventEndAt")
    @Mapping(target = "timezoneName", source = "source", qualifiedByName = "deriveTimezone")
    @Mapping(target = "venueName", source = "source", qualifiedByName = "deriveVenueName")
    @Mapping(target = "venueJson", source = "source.venue", qualifiedByName = "jsonNodeToString")
    @Mapping(target = "photosJson", source = "source", qualifiedByName = "derivePhotosJson")
    @Mapping(target = "rawPayloadJson", expression = "java(rawPayloadJson)")
    ImportedEventData toImportedEventData(MegatixEventDetailsDto source, String rawPayloadJson);

    @Mapping(target = "megatixOrganiserId", source = "source.id")
    @Mapping(target = "rawPayloadJson", expression = "java(rawPayloadJson)")
    ImportedOrganiserData toImportedOrganiserData(MegatixPromoterDto source, String rawPayloadJson);

    @Mapping(target = "rawPayloadJson", source = "source", qualifiedByName = "settlementDetailsToRawJson")
    ImportedSettlementDetailsData toImportedSettlementDetailsData(MegatixSettlementDetailsDto source);

    @Mapping(target = "megatixTierId", source = "source.id")
    @Mapping(target = "facePrice", source = "source.facePrice", qualifiedByName = "defaultPrice")
    @Mapping(target = "salesStartsAt", source = "source.salesStartsAt", qualifiedByName = "toOffsetDateTime")
    @Mapping(target = "salesEndsAt", source = "source.salesEndsAt", qualifiedByName = "toOffsetDateTime")
    @Mapping(target = "displayOrder", source = "source.displayOrder", qualifiedByName = "defaultDisplayOrder")
    @Mapping(target = "active", source = "source.active", qualifiedByName = "defaultActive")
    @Mapping(target = "availabilityState", source = "source", qualifiedByName = "deriveAvailabilityState")
    @Mapping(target = "availabilityCount", source = "source.freeSeatsCount")
    @Mapping(target = "rawPayloadJson", expression = "java(rawPayloadJson)")
    ImportedTicketTierData toImportedTicketTierData(MegatixTicketDto source, String rawPayloadJson);

    @Named("toOffsetDateTime")
    default OffsetDateTime toOffsetDateTime(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            LocalDateTime localDateTime = LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return localDateTime.atOffset(ZoneOffset.UTC);
        }
    }

    @Named("deriveEventStartAt")
    default OffsetDateTime deriveEventStartAt(final MegatixEventDetailsDto source) {
        return parseEventDateTime(source.getStartAt(), deriveTimezone(source));
    }

    @Named("deriveEventEndAt")
    default OffsetDateTime deriveEventEndAt(final MegatixEventDetailsDto source) {
        return parseEventDateTime(source.getEndAt(), deriveTimezone(source));
    }

    @Named("jsonNodeToString")
    default String jsonNodeToString(final JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return objectMapper().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Megatix JSON node", e);
        }
    }

    @Named("deriveTimezone")
    default String deriveTimezone(final MegatixEventDetailsDto source) {
        if (source.getTimezone() != null && !source.getTimezone().isBlank()) {
            return source.getTimezone();
        }
        JsonNode venue = source.getVenue();
        if (venue != null && venue.hasNonNull("timezone")) {
            return venue.get("timezone").asText();
        }
        return null;
    }

    @Named("deriveVenueName")
    default String deriveVenueName(final MegatixEventDetailsDto source) {
        if (source.getVenueName() != null && !source.getVenueName().isBlank()) {
            return source.getVenueName();
        }
        JsonNode venue = source.getVenue();
        if (venue != null && venue.hasNonNull("name")) {
            return venue.get("name").asText();
        }
        return null;
    }

    default OffsetDateTime parseEventDateTime(final String value, final String timezoneName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            LocalDateTime localDateTime = LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            ZoneId zoneId = timezoneName != null && !timezoneName.isBlank() ? ZoneId.of(timezoneName) : ZoneOffset.UTC;
            return localDateTime.atZone(zoneId).toOffsetDateTime();
        }
    }

    @Named("derivePhotosJson")
    default String derivePhotosJson(final MegatixEventDetailsDto source) {
        if (source.getPhotos() != null && !source.getPhotos().isNull()) {
            return jsonNodeToString(source.getPhotos());
        }
        if (source.getCover() != null && !source.getCover().isBlank()) {
            try {
                return objectMapper().writeValueAsString(List.of(source.getCover()));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize Megatix cover image", e);
            }
        }
        return null;
    }

    @Named("settlementDetailsToRawJson")
    default String settlementDetailsToRawJson(final MegatixSettlementDetailsDto source) {
        if (source == null) {
            return null;
        }
        try {
            return objectMapper().writeValueAsString(source);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Megatix settlement details", e);
        }
    }

    @Named("defaultDisplayOrder")
    default Integer defaultDisplayOrder(final Integer value) {
        return value != null ? value : 0;
    }

    @Named("defaultActive")
    default Boolean defaultActive(final Boolean value) {
        return value != null ? value : Boolean.TRUE;
    }

    @Named("defaultPrice")
    default BigDecimal defaultPrice(final BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    @Named("deriveAvailabilityState")
    default TierAvailabilityState deriveAvailabilityState(final MegatixTicketDto source) {
        if (Boolean.TRUE.equals(source.getSoldOut())
                || Boolean.TRUE.equals(source.getSalesClosed())
                || Boolean.TRUE.equals(source.getAllocationExhausted())) {
            return TierAvailabilityState.SOLD_OUT;
        }
        if (source.getFreeSeatsCount() == null) {
            return TierAvailabilityState.UNKNOWN;
        }
        if (source.getFreeSeatsCount() <= 0) {
            return TierAvailabilityState.SOLD_OUT;
        }
        if (source.getFreeSeatsCount() <= 10) {
            return TierAvailabilityState.LOW;
        }
        return TierAvailabilityState.AVAILABLE;
    }

    default ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
