package app.rubeton.toniq.service;

import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.EventPublicationSettings;
import app.rubeton.toniq.entity.PublicationMode;

import java.time.OffsetDateTime;

public interface EventPublicationService {

    EventPublicationSettings ensurePublicationSettings(Event event);

    EventPublicationSettings publish(Event event, boolean cryptoEnabled, String publicationReason, OffsetDateTime publishedAt);

    EventPublicationSettings unpublish(Event event, boolean cryptoEnabled, String publicationReason, OffsetDateTime unpublishedAt);

    EventPublicationSettings setPublicationMode(Event event, PublicationMode publicationMode, String publicationReason,
                                                OffsetDateTime updatedAt);

    EventPublicationSettings recordMegatixWebhookState(Event event, boolean enabled, String publicationReason,
                                                       OffsetDateTime updatedAt);

    EventPublicationSettings reconcile(Event event, OffsetDateTime updatedAt);

    PublicationDecision getDecision(Event event);
}
