package app.rubeton.toniq.service;

import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.EventPublicationSettings;

import java.time.OffsetDateTime;

public interface EventPublicationService {

    EventPublicationSettings ensurePublicationSettings(Event event);

    EventPublicationSettings publish(Event event, boolean cryptoEnabled, String publicationReason, OffsetDateTime publishedAt);

    EventPublicationSettings unpublish(Event event, boolean cryptoEnabled, String publicationReason, OffsetDateTime unpublishedAt);
}
