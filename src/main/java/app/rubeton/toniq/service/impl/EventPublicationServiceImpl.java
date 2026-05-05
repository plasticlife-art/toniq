package app.rubeton.toniq.service.impl;

import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.EventPublicationSettings;
import app.rubeton.toniq.entity.PublicationState;
import app.rubeton.toniq.service.EventPublicationService;
import io.jmix.core.DataManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EventPublicationServiceImpl implements EventPublicationService {

    private final DataManager dataManager;

    @Transactional
    @Override
    public EventPublicationSettings ensurePublicationSettings(final Event event) {
        Objects.requireNonNull(event, "event must not be null");
        EventPublicationSettings existing = dataManager.load(EventPublicationSettings.class)
                .query("e.event = ?1", event)
                .optional()
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        EventPublicationSettings settings = dataManager.create(EventPublicationSettings.class);
        settings.setEvent(event);
        settings.setCryptoEnabled(false);
        settings.setPublished(false);
        settings.setPublicationState(PublicationState.DRAFT);
        OffsetDateTime now = nowUtc();
        settings.setCreatedAt(now);
        settings.setUpdatedAt(now);
        return dataManager.save(settings);
    }

    @Transactional
    @Override
    public EventPublicationSettings publish(final Event event, final boolean cryptoEnabled, final String publicationReason,
                                            final OffsetDateTime publishedAt) {
        EventPublicationSettings settings = ensurePublicationSettings(event);
        OffsetDateTime effectivePublishedAt = publishedAt != null ? publishedAt : nowUtc();
        settings.setCryptoEnabled(cryptoEnabled);
        settings.setPublished(true);
        settings.setPublicationState(PublicationState.PUBLISHED);
        settings.setPublicationReason(publicationReason);
        if (settings.getFirstPublishedAt() == null) {
            settings.setFirstPublishedAt(effectivePublishedAt);
        }
        settings.setLastPublishedAt(effectivePublishedAt);
        settings.setUpdatedAt(effectivePublishedAt);
        return dataManager.save(settings);
    }

    @Transactional
    @Override
    public EventPublicationSettings unpublish(final Event event, final boolean cryptoEnabled, final String publicationReason,
                                              final OffsetDateTime unpublishedAt) {
        EventPublicationSettings settings = ensurePublicationSettings(event);
        OffsetDateTime effectiveUnpublishedAt = unpublishedAt != null ? unpublishedAt : nowUtc();
        settings.setCryptoEnabled(cryptoEnabled);
        settings.setPublished(false);
        settings.setPublicationState(PublicationState.UNPUBLISHED);
        settings.setPublicationReason(publicationReason);
        settings.setLastUnpublishedAt(effectiveUnpublishedAt);
        settings.setUpdatedAt(effectiveUnpublishedAt);
        return dataManager.save(settings);
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
