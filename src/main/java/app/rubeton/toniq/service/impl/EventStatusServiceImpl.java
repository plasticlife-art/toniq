package app.rubeton.toniq.service.impl;

import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.EventPublicationSettings;
import app.rubeton.toniq.entity.EventStatusOverride;
import app.rubeton.toniq.entity.OverrideStatus;
import app.rubeton.toniq.service.EventStatusService;
import io.jmix.core.DataManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EventStatusServiceImpl implements EventStatusService {

    private final DataManager dataManager;

    @Override
    public Optional<EventStatusOverride> getActiveOverride(final Event event) {
        Objects.requireNonNull(event, "event must not be null");
        return dataManager.load(EventStatusOverride.class)
                .query("e.event = ?1 and e.isActive = true order by e.createdAt desc", event)
                .optional();
    }

    @Override
    public boolean isPubliclyVisible(final Event event) {
        Objects.requireNonNull(event, "event must not be null");
        if (event.getDeletedAt() != null) {
            return false;
        }
        EventPublicationSettings settings = dataManager.load(EventPublicationSettings.class)
                .query("e.event = ?1", event)
                .optional()
                .orElse(null);
        return settings != null && Boolean.TRUE.equals(settings.getPublished());
    }

    @Override
    public String resolveEffectiveStatus(final Event event) {
        if (!isPubliclyVisible(event)) {
            return "unavailable";
        }
        return getActiveOverride(event)
                .map(EventStatusOverride::getOverrideStatus)
                .map(status -> status.name().toLowerCase(Locale.ROOT))
                .orElse(event.getMegatixStatus());
    }

    @Transactional
    @Override
    public EventStatusOverride applyOverride(final Event event, final OverrideStatus overrideStatus,
                                             final OffsetDateTime rescheduledStartAt, final OffsetDateTime rescheduledEndAt,
                                             final String adminNote, final String actorIdentifier) {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(overrideStatus, "overrideStatus must not be null");
        Objects.requireNonNull(actorIdentifier, "actorIdentifier must not be null");
        if (overrideStatus == OverrideStatus.RESCHEDULED && rescheduledStartAt == null) {
            throw new IllegalArgumentException("rescheduledStartAt is required for RESCHEDULED override");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<EventStatusOverride> activeOverrides = dataManager.load(EventStatusOverride.class)
                .query("e.event = ?1 and e.isActive = true", event)
                .list();
        for (EventStatusOverride existing : activeOverrides) {
            existing.setIsActive(false);
            existing.setClearedAt(now);
            existing.setClearedBy(actorIdentifier);
            dataManager.save(existing);
        }

        EventStatusOverride override = dataManager.create(EventStatusOverride.class);
        override.setEvent(event);
        override.setOverrideStatus(overrideStatus);
        override.setRescheduledEventStartAt(rescheduledStartAt);
        override.setRescheduledEventEndAt(rescheduledEndAt);
        override.setAdminNote(adminNote);
        override.setActorIdentifier(actorIdentifier);
        override.setIsActive(true);
        override.setCreatedAt(now);
        return dataManager.save(override);
    }

    @Transactional
    @Override
    public void clearOverride(final Event event, final String actorIdentifier, final OffsetDateTime clearedAt) {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(actorIdentifier, "actorIdentifier must not be null");
        OffsetDateTime effectiveClearedAt = clearedAt != null ? clearedAt : OffsetDateTime.now(ZoneOffset.UTC);
        List<EventStatusOverride> activeOverrides = dataManager.load(EventStatusOverride.class)
                .query("e.event = ?1 and e.isActive = true", event)
                .list();
        for (EventStatusOverride existing : activeOverrides) {
            existing.setIsActive(false);
            existing.setClearedAt(effectiveClearedAt);
            existing.setClearedBy(actorIdentifier);
            dataManager.save(existing);
        }
    }
}
