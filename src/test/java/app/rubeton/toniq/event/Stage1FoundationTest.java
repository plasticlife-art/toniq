package app.rubeton.toniq.event;

import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.EventPublicationSettings;
import app.rubeton.toniq.entity.EventStatusOverride;
import app.rubeton.toniq.entity.EventSyncLog;
import app.rubeton.toniq.entity.EventSyncState;
import app.rubeton.toniq.entity.EventTicketTier;
import app.rubeton.toniq.entity.Organiser;
import app.rubeton.toniq.entity.OverrideStatus;
import app.rubeton.toniq.entity.PublicationState;
import app.rubeton.toniq.entity.SyncLogScope;
import app.rubeton.toniq.entity.SyncLogStatus;
import app.rubeton.toniq.entity.SyncLogTriggerSource;
import app.rubeton.toniq.entity.SyncResult;
import app.rubeton.toniq.entity.TierAvailabilityState;
import app.rubeton.toniq.service.AvailabilityRefreshService;
import app.rubeton.toniq.service.EventPublicationService;
import app.rubeton.toniq.service.EventStatusService;
import app.rubeton.toniq.service.EventSyncService;
import app.rubeton.toniq.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.data.exception.UniqueConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class Stage1FoundationTest {

    @Autowired
    DataManager dataManager;

    @Autowired
    EventPublicationService eventPublicationService;

    @Autowired
    EventStatusService eventStatusService;

    @Autowired
    EventSyncService eventSyncService;

    @Autowired
    AvailabilityRefreshService availabilityRefreshService;

    @AfterEach
    void tearDown() {
        dataManager.load(EventStatusOverride.class)
                .all()
                .list()
                .forEach(dataManager::remove);
        dataManager.load(EventSyncLog.class)
                .all()
                .list()
                .forEach(dataManager::remove);
        dataManager.load(EventTicketTier.class)
                .all()
                .list()
                .forEach(dataManager::remove);
        dataManager.load(EventPublicationSettings.class)
                .all()
                .list()
                .forEach(dataManager::remove);
        dataManager.load(EventSyncState.class)
                .all()
                .list()
                .forEach(dataManager::remove);
        dataManager.load(Event.class)
                .all()
                .list()
                .forEach(dataManager::remove);
        dataManager.load(Organiser.class)
                .all()
                .list()
                .forEach(dataManager::remove);
    }

    @Test
    void test_schemaSupportsPersistenceAndRelations() {
        Organiser organiser = saveOrganiser(uniqueSuffix());
        Event event = saveEvent(organiser, uniqueSuffix());
        EventPublicationSettings publicationSettings = eventPublicationService.ensurePublicationSettings(event);
        EventSyncState syncState = eventSyncService.ensureSyncState(event);
        EventTicketTier tier = saveTier(event, uniqueSuffix());
        EventSyncLog syncLog = eventSyncService.recordSyncStarted(event.getMegatixEventId(), event,
                SyncLogTriggerSource.MANUAL, SyncLogScope.FULL_IMPORT, "{\"requested\":true}");
        EventStatusOverride override = eventStatusService.applyOverride(event, OverrideStatus.CANCELLED,
                null, null, "ops override", "admin");

        assertThat(publicationSettings.getEvent().getId()).isEqualTo(event.getId());
        assertThat(syncState.getEvent().getId()).isEqualTo(event.getId());
        assertThat(tier.getEvent().getId()).isEqualTo(event.getId());
        assertThat(syncLog.getEvent().getId()).isEqualTo(event.getId());
        assertThat(override.getEvent().getId()).isEqualTo(event.getId());
    }

    @Test
    void test_uniqueConstraintsAndNullableSyncLogEvent() {
        Organiser organiser = saveOrganiser(uniqueSuffix());
        Event event = saveEvent(organiser, uniqueSuffix());
        eventPublicationService.ensurePublicationSettings(event);
        eventSyncService.ensureSyncState(event);

        Event duplicateEvent = dataManager.create(Event.class);
        duplicateEvent.setMegatixEventId(event.getMegatixEventId());
        duplicateEvent.setOrganiser(organiser);
        duplicateEvent.setTitle("Duplicate event");

        assertThatThrownBy(() -> dataManager.save(duplicateEvent))
                .isInstanceOf(UniqueConstraintViolationException.class);

        EventSyncLog syncLog = eventSyncService.recordSyncStarted("external-only-" + uniqueSuffix(), null,
                SyncLogTriggerSource.MANUAL, SyncLogScope.FULL_IMPORT, null);
        assertThat(syncLog.getEvent()).isNull();

        EventPublicationSettings secondSettings = dataManager.create(EventPublicationSettings.class);
        secondSettings.setEvent(event);
        secondSettings.setCryptoEnabled(true);
        secondSettings.setPublished(true);
        secondSettings.setPublicationState(PublicationState.PUBLISHED);

        assertThatThrownBy(() -> dataManager.save(secondSettings))
                .isInstanceOf(UniqueConstraintViolationException.class);
    }

    @Test
    void test_publicationStatusAndOverrideLifecycle() {
        Organiser organiser = saveOrganiser(uniqueSuffix());
        Event event = saveEvent(organiser, uniqueSuffix());

        EventPublicationSettings unpublished = eventPublicationService.unpublish(event, false, "crypto_disabled", nowUtc());
        assertThat(unpublished.getCryptoEnabled()).isFalse();
        assertThat(unpublished.getPublished()).isFalse();
        assertThat(unpublished.getPublicationState()).isEqualTo(PublicationState.UNPUBLISHED);
        assertThat(eventStatusService.isPubliclyVisible(event)).isFalse();
        assertThat(eventStatusService.resolveEffectiveStatus(event)).isEqualTo("unavailable");

        eventPublicationService.publish(event, true, "crypto_enabled", nowUtc());
        EventStatusOverride firstOverride = eventStatusService.applyOverride(event, OverrideStatus.RESCHEDULED,
                OffsetDateTime.parse("2026-05-06T10:15:30Z"), OffsetDateTime.parse("2026-05-06T12:15:30Z"), "new slot", "admin");
        assertThat(eventStatusService.resolveEffectiveStatus(event)).isEqualTo("rescheduled");

        EventStatusOverride secondOverride = eventStatusService.applyOverride(event, OverrideStatus.CANCELLED,
                null, null, "cancelled later", "admin");
        EventStatusOverride reloadedFirst = dataManager.load(EventStatusOverride.class).id(firstOverride.getId()).one();
        assertThat(reloadedFirst.getIsActive()).isFalse();
        assertThat(secondOverride.getIsActive()).isTrue();

        eventStatusService.clearOverride(event, "ops", OffsetDateTime.parse("2026-05-07T10:15:30Z"));
        List<EventStatusOverride> overrides = dataManager.load(EventStatusOverride.class)
                .query("e.event = ?1 order by e.createdAt", event)
                .list();
        assertThat(overrides).allMatch(o -> Boolean.FALSE.equals(o.getIsActive()));
    }

    @Test
    void test_effectiveStatusFallsBackToActiveWhenNoOverrideExists() {
        Organiser organiser = saveOrganiser(uniqueSuffix());
        Event event = saveEvent(organiser, uniqueSuffix());
        eventPublicationService.publish(event, true, "crypto_enabled", nowUtc());

        assertThat(eventStatusService.resolveEffectiveStatus(event)).isEqualTo("active");
    }

    @Test
    void test_syncStateTierUpdatesAndSoftDeleteSemantics() {
        Organiser organiser = saveOrganiser(uniqueSuffix());
        Event event = saveEvent(organiser, uniqueSuffix());
        EventTicketTier tier = saveTier(event, uniqueSuffix());

        EventSyncLog started = eventSyncService.recordSyncStarted(event.getMegatixEventId(), event,
                SyncLogTriggerSource.POLLING, SyncLogScope.EVENT_REFRESH, null);
        eventSyncService.recordSyncFailure(started, event, "megatix_timeout", "timeout", null);

        EventSyncState failedSyncState = dataManager.load(EventSyncState.class)
                .query("e.event = ?1", event)
                .one();
        assertThat(failedSyncState.getLastSyncResult()).isEqualTo(SyncResult.FAILURE);
        assertThat(failedSyncState.getLastSyncError()).isEqualTo("timeout");

        EventSyncLog availabilityStarted = eventSyncService.recordSyncStarted(event.getMegatixEventId(), event,
                SyncLogTriggerSource.POLLING, SyncLogScope.AVAILABILITY_REFRESH, null);
        eventSyncService.recordSyncSuccess(availabilityStarted, event, "{\"ok\":true}");

        EventSyncState successSyncState = dataManager.load(EventSyncState.class)
                .query("e.event = ?1", event)
                .one();
        assertThat(successSyncState.getLastSyncResult()).isEqualTo(SyncResult.SUCCESS);
        assertThat(successSyncState.getLastAvailabilitySyncAt()).isNotNull();

        EventTicketTier updatedTier = availabilityRefreshService.updateTierAvailability(tier, 7,
                TierAvailabilityState.LOW, nowUtc());
        assertThat(updatedTier.getAvailabilityCount()).isEqualTo(7);
        assertThat(updatedTier.getAvailabilityState()).isEqualTo(TierAvailabilityState.LOW);

        updatedTier.setIsActive(false);
        dataManager.save(updatedTier);
        assertThat(dataManager.load(EventTicketTier.class).id(updatedTier.getId()).one().getIsActive()).isFalse();

        event.setDeletedAt(nowUtc());
        event.setDeletedBy("ops");
        dataManager.save(event);

        EventSyncLog logAfterDelete = eventSyncService.recordSyncStarted(event.getMegatixEventId(), event,
                SyncLogTriggerSource.MANUAL, SyncLogScope.UNPUBLISH, null);
        EventStatusOverride overrideAfterDelete = eventStatusService.applyOverride(event, OverrideStatus.COMPLETED,
                null, null, "complete for audit", "admin");

        assertThat(eventStatusService.isPubliclyVisible(event)).isFalse();
        assertThat(eventStatusService.resolveEffectiveStatus(event)).isEqualTo("unavailable");
        assertThat(logAfterDelete.getEvent().getId()).isEqualTo(event.getId());
        assertThat(overrideAfterDelete.getEvent().getId()).isEqualTo(event.getId());
    }

    private Organiser saveOrganiser(final String suffix) {
        Organiser organiser = dataManager.create(Organiser.class);
        organiser.setMegatixOrganiserId("org-" + suffix);
        organiser.setName("Organiser " + suffix);
        organiser.setEmail("ops+" + suffix + "@example.com");
        organiser.setRawPayloadJson("{\"organiser\":\"" + suffix + "\"}");
        organiser.setCreatedAt(nowUtc());
        organiser.setUpdatedAt(nowUtc());
        return dataManager.save(organiser);
    }

    private Event saveEvent(final Organiser organiser, final String suffix) {
        Event event = dataManager.create(Event.class);
        event.setMegatixEventId("event-" + suffix);
        event.setOrganiser(organiser);
        event.setTitle("Event " + suffix);
        event.setDescription("Description " + suffix);
        event.setVenueName("Venue " + suffix);
        event.setRawPayloadJson("{\"event\":\"" + suffix + "\"}");
        event.setCreatedAt(nowUtc());
        event.setUpdatedAt(nowUtc());
        return dataManager.save(event);
    }

    private EventTicketTier saveTier(final Event event, final String suffix) {
        EventTicketTier tier = dataManager.create(EventTicketTier.class);
        tier.setEvent(event);
        tier.setMegatixTierId("tier-" + suffix);
        tier.setName("Tier " + suffix);
        tier.setCurrencyCode("EUR");
        tier.setFacePrice(new BigDecimal("42.50"));
        tier.setDisplayOrder(1);
        tier.setCreatedAt(nowUtc());
        tier.setUpdatedAt(nowUtc());
        return dataManager.save(tier);
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString();
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
