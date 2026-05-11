package app.rubeton.toniq.service.publicweb;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PublicEventDetailResponse(
        EventDto event,
        ScheduleDto schedule,
        VenueDto venue,
        StatusDto status,
        AvailabilityDto availability,
        List<TicketDto> tickets,
        CtaDto cta
) {

    public record EventDto(
            UUID id,
            String megatixEventId,
            String slug,
            String title,
            String organiserName,
            String descriptionHtml,
            String heroImageUrl,
            List<String> galleryImageUrls
    ) {
    }

    public record ScheduleDto(
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            String timezone
    ) {
    }

    public record VenueDto(
            String name,
            String address,
            String suburb,
            String countryCode
    ) {
    }

    public record StatusDto(
            String effectiveStatus,
            String statusLabel,
            OffsetDateTime rescheduledStartAt,
            OffsetDateTime rescheduledEndAt
    ) {
    }

    public record AvailabilityDto(
            OffsetDateTime lastUpdatedAt,
            String source
    ) {
    }

    public record TicketDto(
            String name,
            String description,
            String currencyCode,
            BigDecimal facePrice,
            Integer availabilityCount,
            String availabilityState,
            OffsetDateTime salesStartsAt,
            OffsetDateTime salesEndsAt
    ) {
    }

    public record CtaDto(
            String label,
            String url,
            String kind
    ) {
    }
}
