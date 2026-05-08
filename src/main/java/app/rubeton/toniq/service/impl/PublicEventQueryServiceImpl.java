package app.rubeton.toniq.service.impl;

import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.EventStatusOverride;
import app.rubeton.toniq.entity.EventTicketTier;
import app.rubeton.toniq.entity.OverrideStatus;
import app.rubeton.toniq.service.EventStatusService;
import app.rubeton.toniq.service.PublicContentSanitizer;
import app.rubeton.toniq.service.PublicEventQueryService;
import app.rubeton.toniq.service.publicweb.PublicEventDetailResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PublicEventQueryServiceImpl implements PublicEventQueryService {

    private static final Comparator<EventTicketTier> TICKET_ORDER =
            Comparator.comparing(EventTicketTier::getDisplayOrder, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(EventTicketTier::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    private final DataManager dataManager;
    private final EventStatusService eventStatusService;
    private final PublicContentSanitizer publicContentSanitizer;
    private final ObjectMapper objectMapper;
    private final SystemAuthenticator systemAuthenticator;

    @Transactional(readOnly = true)
    @Override
    public Optional<PublicEventDetailResponse> findPublishedEventBySlug(final String slug) {
        return systemAuthenticator.withSystem(() -> doFindBySlug(slug));
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<PublicEventDetailResponse> findPublishedEventByMegatixId(final String megatixEventId) {
        return systemAuthenticator.withSystem(() -> doFindByMegatixId(megatixEventId));
    }

    private Optional<PublicEventDetailResponse> doFindBySlug(final String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return dataManager.load(Event.class)
                .query("e.slug = ?1", slug)
                .optional()
                .filter(eventStatusService::isPubliclyVisible)
                .map(this::toResponse);
    }

    private Optional<PublicEventDetailResponse> doFindByMegatixId(final String megatixEventId) {
        if (megatixEventId == null || megatixEventId.isBlank()) {
            return Optional.empty();
        }
        return dataManager.load(Event.class)
                .query("e.megatixEventId = ?1", megatixEventId)
                .optional()
                .filter(eventStatusService::isPubliclyVisible)
                .map(this::toResponse);
    }

    private PublicEventDetailResponse toResponse(final Event event) {
        List<String> galleryImageUrls = readPhotoUrls(event.getPhotosJson());
        String effectiveStatus = eventStatusService.resolveEffectiveStatus(event);
        EventStatusOverride activeOverride = eventStatusService.getActiveOverride(event).orElse(null);
        JsonNode venueNode = readJsonObject(event.getVenueJson());
        List<PublicEventDetailResponse.TicketDto> activeTickets = loadActiveTickets(event);

        return new PublicEventDetailResponse(
                new PublicEventDetailResponse.EventDto(
                        event.getId(),
                        event.getSlug(),
                        event.getTitle(),
                        event.getOrganiser() != null ? event.getOrganiser().getName() : null,
                        publicContentSanitizer.sanitizeDescriptionHtml(event.getDescription()),
                        galleryImageUrls.isEmpty() ? null : galleryImageUrls.get(0),
                        galleryImageUrls
                ),
                new PublicEventDetailResponse.ScheduleDto(
                        event.getEventStartAt(),
                        event.getEventEndAt(),
                        event.getTimezoneName()
                ),
                new PublicEventDetailResponse.VenueDto(
                        event.getVenueName(),
                        textValue(venueNode, "full_address"),
                        textValue(venueNode, "suburb"),
                        textValue(venueNode, "country_code")
                ),
                new PublicEventDetailResponse.StatusDto(
                        effectiveStatus,
                        toStatusLabel(effectiveStatus),
                        activeOverride != null && activeOverride.getOverrideStatus() == OverrideStatus.RESCHEDULED
                                ? activeOverride.getRescheduledEventStartAt()
                                : null,
                        activeOverride != null && activeOverride.getOverrideStatus() == OverrideStatus.RESCHEDULED
                                ? activeOverride.getRescheduledEventEndAt()
                                : null
                ),
                activeTickets,
                resolveCta(effectiveStatus, activeTickets)
        );
    }

    private List<PublicEventDetailResponse.TicketDto> loadActiveTickets(final Event event) {
        List<EventTicketTier> tiers = dataManager.load(EventTicketTier.class)
                .query("e.event = ?1 and e.isActive = true", event)
                .list();
        tiers.sort(TICKET_ORDER);

        List<PublicEventDetailResponse.TicketDto> ticketDtos = new ArrayList<>();
        for (EventTicketTier tier : tiers) {
            ticketDtos.add(new PublicEventDetailResponse.TicketDto(
                    tier.getName(),
                    tier.getDescription(),
                    tier.getCurrencyCode(),
                    tier.getFacePrice(),
                    tier.getAvailabilityCount(),
                    tier.getAvailabilityState() != null ? tier.getAvailabilityState().getId() : null,
                    tier.getSalesStartsAt(),
                    tier.getSalesEndsAt()
            ));
        }
        return ticketDtos;
    }

    private PublicEventDetailResponse.CtaDto resolveCta(final String effectiveStatus,
                                                        final List<PublicEventDetailResponse.TicketDto> activeTiers) {
        if ("cancelled".equals(effectiveStatus)) {
            return new PublicEventDetailResponse.CtaDto("Event unavailable", null, "placeholder");
        }
        if ("rescheduled".equals(effectiveStatus)) {
            return new PublicEventDetailResponse.CtaDto("Schedule updated", null, "placeholder");
        }
        if (activeTiers.isEmpty()) {
            return new PublicEventDetailResponse.CtaDto("Sales information pending", null, "placeholder");
        }
        return new PublicEventDetailResponse.CtaDto("Ticket link coming soon", null, "placeholder");
    }

    private String toStatusLabel(final String effectiveStatus) {
        if (effectiveStatus == null || effectiveStatus.isBlank()) {
            return "Active";
        }
        return switch (effectiveStatus) {
            case "active" -> "Active";
            case "cancelled" -> "Cancelled";
            case "rescheduled" -> "Rescheduled";
            case "completed" -> "Completed";
            default -> effectiveStatus.substring(0, 1).toUpperCase(Locale.ROOT) + effectiveStatus.substring(1);
        };
    }

    private List<String> readPhotoUrls(final String photosJson) {
        JsonNode arrayNode = readJsonArray(photosJson);
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }

        List<String> urls = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            String value = node.asText(null);
            if (value != null && !value.isBlank()) {
                urls.add(value);
            }
        }
        return urls;
    }

    private JsonNode readJsonObject(final String json) {
        try {
            JsonNode node = json == null || json.isBlank() ? null : objectMapper.readTree(json);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode readJsonArray(final String json) {
        try {
            JsonNode node = json == null || json.isBlank() ? null : objectMapper.readTree(json);
            return node != null && node.isArray() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String textValue(final JsonNode node, final String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        String value = field.asText(null);
        return value == null || value.isBlank() ? null : value;
    }
}
