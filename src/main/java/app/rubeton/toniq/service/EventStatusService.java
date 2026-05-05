package app.rubeton.toniq.service;

import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.EventStatusOverride;
import app.rubeton.toniq.entity.OverrideStatus;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface EventStatusService {

    Optional<EventStatusOverride> getActiveOverride(Event event);

    boolean isPubliclyVisible(Event event);

    String resolveEffectiveStatus(Event event);

    EventStatusOverride applyOverride(Event event, OverrideStatus overrideStatus, OffsetDateTime rescheduledStartAt,
                                      OffsetDateTime rescheduledEndAt, String adminNote, String actorIdentifier);

    void clearOverride(Event event, String actorIdentifier, OffsetDateTime clearedAt);
}
