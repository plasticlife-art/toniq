package app.rubeton.toniq.service.impl;

import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.EventPublicationSettings;
import app.rubeton.toniq.entity.PublicationMode;
import app.rubeton.toniq.entity.PublicationState;
import app.rubeton.toniq.service.PublicationDecision;
import app.rubeton.toniq.service.EventPublicationService;
import app.rubeton.toniq.service.SroastProjectionService;
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
    private final SroastProjectionService sroastProjectionService;

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
        settings.setPublicationMode(PublicationMode.AUTO);
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
        settings.setPublicationReason(publicationReason);
        if (settings.getFirstPublishedAt() == null) {
            settings.setFirstPublishedAt(effectivePublishedAt);
        }
        settings.setLastPublishedAt(effectivePublishedAt);
        settings.setUpdatedAt(effectivePublishedAt);
        return reconcileAndSave(settings, effectivePublishedAt);
    }

    @Transactional
    @Override
    public EventPublicationSettings unpublish(final Event event, final boolean cryptoEnabled, final String publicationReason,
                                              final OffsetDateTime unpublishedAt) {
        EventPublicationSettings settings = ensurePublicationSettings(event);
        OffsetDateTime effectiveUnpublishedAt = unpublishedAt != null ? unpublishedAt : nowUtc();
        settings.setCryptoEnabled(cryptoEnabled);
        settings.setPublished(false);
        settings.setPublicationReason(publicationReason);
        settings.setLastUnpublishedAt(effectiveUnpublishedAt);
        settings.setUpdatedAt(effectiveUnpublishedAt);
        return reconcileAndSave(settings, effectiveUnpublishedAt);
    }

    @Transactional
    @Override
    public EventPublicationSettings setPublicationMode(final Event event, final PublicationMode publicationMode,
                                                       final String publicationReason, final OffsetDateTime updatedAt) {
        Objects.requireNonNull(publicationMode, "publicationMode must not be null");
        EventPublicationSettings settings = ensurePublicationSettings(event);
        OffsetDateTime effectiveUpdatedAt = updatedAt != null ? updatedAt : nowUtc();
        settings.setPublicationMode(publicationMode);
        settings.setPublicationReason(publicationReason);
        settings.setUpdatedAt(effectiveUpdatedAt);
        if (publicationMode == PublicationMode.ON) {
            settings.setCryptoEnabled(true);
            settings.setPublished(true);
        }
        return reconcileAndSave(settings, effectiveUpdatedAt);
    }

    @Transactional
    @Override
    public EventPublicationSettings recordMegatixWebhookState(final Event event, final boolean enabled,
                                                              final String publicationReason,
                                                              final OffsetDateTime updatedAt) {
        EventPublicationSettings settings = ensurePublicationSettings(event);
        OffsetDateTime effectiveUpdatedAt = updatedAt != null ? updatedAt : nowUtc();
        settings.setLastMegatixWebhookEnabled(enabled);
        settings.setLastMegatixWebhookAt(effectiveUpdatedAt);
        if (enabled) {
            settings.setCryptoEnabled(true);
            settings.setPublished(true);
        } else if (settings.getPublicationMode() != PublicationMode.ON) {
            settings.setCryptoEnabled(false);
            settings.setPublished(false);
        }
        settings.setPublicationReason(publicationReason);
        settings.setUpdatedAt(effectiveUpdatedAt);
        if (enabled && settings.getFirstPublishedAt() == null) {
            settings.setFirstPublishedAt(effectiveUpdatedAt);
        }
        if (enabled) {
            settings.setLastPublishedAt(effectiveUpdatedAt);
        } else {
            settings.setLastUnpublishedAt(effectiveUpdatedAt);
        }
        return reconcileAndSave(settings, effectiveUpdatedAt);
    }

    @Transactional
    @Override
    public EventPublicationSettings reconcile(final Event event, final OffsetDateTime updatedAt) {
        EventPublicationSettings settings = ensurePublicationSettings(event);
        OffsetDateTime effectiveUpdatedAt = updatedAt != null ? updatedAt : nowUtc();
        settings.setUpdatedAt(effectiveUpdatedAt);
        return reconcileAndSave(settings, effectiveUpdatedAt);
    }

    @Override
    public PublicationDecision getDecision(final Event event) {
        Objects.requireNonNull(event, "event must not be null");
        EventPublicationSettings settings = ensurePublicationSettings(event);
        return evaluate(event, settings);
    }

    private EventPublicationSettings reconcileAndSave(final EventPublicationSettings settings, final OffsetDateTime updatedAt) {
        PublicationDecision decision = evaluate(settings.getEvent(), settings);
        settings.setPublicationState(decision.effectivePublished() ? PublicationState.PUBLISHED : PublicationState.UNPUBLISHED);
        if (!decision.effectivePublished()) {
            settings.setLastUnpublishedAt(updatedAt);
        }
        return dataManager.save(settings);
    }

    private PublicationDecision evaluate(final Event event, final EventPublicationSettings settings) {
        boolean deletedGatePassed = event.getDeletedAt() == null;
        boolean sroastGatePassed = sroastProjectionService.existsForEvent(event);
        PublicationMode publicationMode = settings.getPublicationMode() != null ? settings.getPublicationMode() : PublicationMode.AUTO;
        Boolean lastMegatixWebhookEnabled = settings.getLastMegatixWebhookEnabled();
        boolean webhookReceived = lastMegatixWebhookEnabled != null;

        boolean desiredPublished = switch (publicationMode) {
            case ON -> true;
            case OFF -> false;
            case AUTO -> Boolean.TRUE.equals(lastMegatixWebhookEnabled);
        };

        boolean effectivePublished = desiredPublished
                && deletedGatePassed
                && sroastGatePassed;

        return new PublicationDecision(
                publicationMode,
                lastMegatixWebhookEnabled,
                webhookReceived,
                deletedGatePassed,
                sroastGatePassed,
                effectivePublished,
                resolveBlockedReason(publicationMode, lastMegatixWebhookEnabled, webhookReceived, deletedGatePassed, sroastGatePassed),
                resolveWebhookHint(lastMegatixWebhookEnabled)
        );
    }

    private String resolveBlockedReason(final PublicationMode publicationMode,
                                        final Boolean lastMegatixWebhookEnabled,
                                        final boolean webhookReceived,
                                        final boolean deletedGatePassed,
                                        final boolean sroastGatePassed) {
        if (publicationMode == PublicationMode.OFF) {
            return "Disabled in admin";
        }
        if (!deletedGatePassed) {
            return "Event soft-deleted";
        }
        if (!sroastGatePassed) {
            return "Missing SROAST projection";
        }
        if (publicationMode == PublicationMode.AUTO && !webhookReceived) {
            return "No Megatix enable webhook yet";
        }
        if (publicationMode == PublicationMode.AUTO && Boolean.FALSE.equals(lastMegatixWebhookEnabled)) {
            return "Disabled by latest Megatix webhook";
        }
        return null;
    }

    private String resolveWebhookHint(final Boolean lastMegatixWebhookEnabled) {
        if (lastMegatixWebhookEnabled == null) {
            return "Megatix: no webhook yet";
        }
        return Boolean.TRUE.equals(lastMegatixWebhookEnabled)
                ? "Megatix: enabled"
                : "Megatix: disabled";
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
