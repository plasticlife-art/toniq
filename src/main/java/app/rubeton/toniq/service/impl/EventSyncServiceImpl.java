package app.rubeton.toniq.service.impl;

import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.EventSyncLog;
import app.rubeton.toniq.entity.EventSyncState;
import app.rubeton.toniq.entity.EventTicketTier;
import app.rubeton.toniq.entity.Organiser;
import app.rubeton.toniq.entity.OrganiserSettlementDetails;
import app.rubeton.toniq.entity.SyncLogScope;
import app.rubeton.toniq.entity.SyncLogStatus;
import app.rubeton.toniq.entity.SyncLogTriggerSource;
import app.rubeton.toniq.entity.SyncResult;
import app.rubeton.toniq.service.EventPublicationService;
import app.rubeton.toniq.service.EventSyncService;
import app.rubeton.toniq.service.MegatixClient;
import app.rubeton.toniq.service.megatix.mapper.MegatixImportMapper;
import app.rubeton.toniq.service.megatix.model.ImportedEventData;
import app.rubeton.toniq.service.megatix.model.ImportedOrganiserData;
import app.rubeton.toniq.service.megatix.model.ImportedTicketTierData;
import app.rubeton.toniq.service.megatix.model.MegatixEventDetailsResponse;
import app.rubeton.toniq.service.megatix.model.MegatixPromoterResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EventSyncServiceImpl implements EventSyncService {

    private final DataManager dataManager;
    private final MegatixClient megatixClient;
    private final MegatixImportMapper megatixImportMapper;
    private final EventPublicationService eventPublicationService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Event importEvent(final String eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        MegatixEventDetailsResponse eventResponse = megatixClient.fetchEventDetails(eventId);
        MegatixPromoterResponse promoterResponse = megatixClient.fetchEventPromoter(eventId);

        ImportedOrganiserData organiserData = megatixImportMapper.toImportedOrganiserData(
                promoterResponse.payload(), promoterResponse.rawPayloadJson());
        if (organiserData.getMegatixOrganiserId() == null || organiserData.getMegatixOrganiserId().isBlank()) {
            organiserData.setMegatixOrganiserId(eventResponse.payload().getPromoterId());
        }
        requireNonBlank(organiserData.getMegatixOrganiserId(), "Megatix organiser ID is required");

        Organiser organiser = upsertOrganiser(organiserData);
        ImportedEventData eventData = megatixImportMapper.toImportedEventData(eventResponse.payload(), eventResponse.rawPayloadJson());
        requireNonBlank(eventData.getMegatixEventId(), "Megatix event ID is required");
        requireNonBlank(eventData.getTitle(), "Megatix event title is required");

        Event event = upsertEvent(eventData, organiser);
        eventPublicationService.ensurePublicationSettings(event);
        ensureSyncState(event);
        syncTicketTiers(event, eventResponse);
        return event;
    }

    @Transactional
    @Override
    public EventSyncState ensureSyncState(final Event event) {
        Objects.requireNonNull(event, "event must not be null");
        EventSyncState existing = dataManager.load(EventSyncState.class)
                .query("e.event = ?1", event)
                .optional()
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        EventSyncState syncState = dataManager.create(EventSyncState.class);
        syncState.setEvent(event);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        syncState.setCreatedAt(now);
        syncState.setUpdatedAt(now);
        return dataManager.save(syncState);
    }

    @Transactional
    @Override
    public EventSyncLog recordSyncStarted(final String megatixEventId, final Event event,
                                          final SyncLogTriggerSource triggerSource, final SyncLogScope syncScope,
                                          final String requestPayloadJson) {
        EventSyncLog syncLog = dataManager.create(EventSyncLog.class);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        syncLog.setEvent(event);
        syncLog.setMegatixEventId(megatixEventId);
        syncLog.setTriggerSource(triggerSource);
        syncLog.setSyncScope(syncScope);
        syncLog.setStatus(SyncLogStatus.STARTED);
        syncLog.setRequestPayloadJson(requestPayloadJson);
        syncLog.setStartedAt(now);
        syncLog.setCreatedAt(now);
        return dataManager.save(syncLog);
    }

    @Transactional
    @Override
    public EventSyncLog recordSyncSuccess(final EventSyncLog syncLog, final Event event, final String responsePayloadJson) {
        Objects.requireNonNull(syncLog, "syncLog must not be null");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        syncLog.setEvent(event != null ? event : syncLog.getEvent());
        syncLog.setStatus(SyncLogStatus.SUCCESS);
        syncLog.setResponsePayloadJson(responsePayloadJson);
        syncLog.setFinishedAt(now);
        EventSyncLog savedLog = dataManager.save(syncLog);

        if (savedLog.getEvent() != null) {
            EventSyncState syncState = ensureSyncState(savedLog.getEvent());
            syncState.setLastSyncedAt(now);
            syncState.setLastSyncResult(SyncResult.SUCCESS);
            syncState.setLastSyncError(null);
            if (savedLog.getSyncScope() == SyncLogScope.AVAILABILITY_REFRESH) {
                syncState.setLastAvailabilitySyncAt(now);
            } else {
                syncState.setLastEventDataSyncAt(now);
                if (savedLog.getSyncScope() == SyncLogScope.FULL_IMPORT
                        || savedLog.getSyncScope() == SyncLogScope.EVENT_REFRESH) {
                    syncState.setLastAvailabilitySyncAt(now);
                }
            }
            syncState.setUpdatedAt(now);
            dataManager.save(syncState);
        }

        return savedLog;
    }

    @Transactional
    @Override
    public EventSyncLog recordSyncFailure(final EventSyncLog syncLog, final Event event, final String errorCode,
                                          final String errorMessage, final String responsePayloadJson) {
        Objects.requireNonNull(syncLog, "syncLog must not be null");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        syncLog.setEvent(event != null ? event : syncLog.getEvent());
        syncLog.setStatus(SyncLogStatus.FAILURE);
        syncLog.setErrorCode(errorCode);
        syncLog.setErrorMessage(errorMessage);
        syncLog.setResponsePayloadJson(responsePayloadJson);
        syncLog.setFinishedAt(now);
        EventSyncLog savedLog = dataManager.save(syncLog);

        if (savedLog.getEvent() != null) {
            EventSyncState syncState = ensureSyncState(savedLog.getEvent());
            syncState.setLastSyncedAt(now);
            syncState.setLastSyncResult(SyncResult.FAILURE);
            syncState.setLastSyncError(errorMessage);
            syncState.setUpdatedAt(now);
            dataManager.save(syncState);
        }

        return savedLog;
    }

    @Transactional
    @Override
    public EventSyncLog recordSyncIgnored(final EventSyncLog syncLog, final Event event, final String reasonCode,
                                          final String reasonMessage, final String responsePayloadJson) {
        Objects.requireNonNull(syncLog, "syncLog must not be null");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        syncLog.setEvent(event != null ? event : syncLog.getEvent());
        syncLog.setStatus(SyncLogStatus.IGNORED);
        syncLog.setErrorCode(reasonCode);
        syncLog.setErrorMessage(reasonMessage);
        syncLog.setResponsePayloadJson(responsePayloadJson);
        syncLog.setFinishedAt(now);
        return dataManager.save(syncLog);
    }

    private Organiser upsertOrganiser(final ImportedOrganiserData organiserData) {
        Organiser organiser = dataManager.load(Organiser.class)
                .query("e.megatixOrganiserId = ?1", organiserData.getMegatixOrganiserId())
                .optional()
                .orElseGet(() -> dataManager.create(Organiser.class));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (organiser.getCreatedAt() == null) {
            organiser.setCreatedAt(now);
        }
        organiser.setMegatixOrganiserId(organiserData.getMegatixOrganiserId());
        organiser.setName(organiserData.getName());
        organiser.setEmail(organiserData.getEmail());
        organiser.setRawPayloadJson(organiserData.getRawPayloadJson());
        organiser.setUpdatedAt(now);
        Organiser savedOrganiser = dataManager.save(organiser);
        upsertSettlementDetails(savedOrganiser, organiserData);
        return savedOrganiser;
    }

    private void upsertSettlementDetails(final Organiser organiser, final ImportedOrganiserData organiserData) {
        if (organiserData.getSettlementDetails() == null) {
            OrganiserSettlementDetails existing = dataManager.load(OrganiserSettlementDetails.class)
                    .query("e.organiser = ?1", organiser)
                    .optional()
                    .orElse(null);
            if (existing != null) {
                dataManager.remove(existing);
            }
            return;
        }

        OrganiserSettlementDetails settlementDetails = dataManager.load(OrganiserSettlementDetails.class)
                .query("e.organiser = ?1", organiser)
                .optional()
                .orElseGet(() -> dataManager.create(OrganiserSettlementDetails.class));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (settlementDetails.getCreatedAt() == null) {
            settlementDetails.setCreatedAt(now);
        }
        settlementDetails.setOrganiser(organiser);
        settlementDetails.setAccountNumber(organiserData.getSettlementDetails().getAccountNumber());
        settlementDetails.setAccountBsb(organiserData.getSettlementDetails().getAccountBsb());
        settlementDetails.setAccountName(organiserData.getSettlementDetails().getAccountName());
        settlementDetails.setCountryCode(organiserData.getSettlementDetails().getCountryCode());
        settlementDetails.setWalletAddress(organiserData.getSettlementDetails().getWalletAddress());
        settlementDetails.setRawPayloadJson(organiserData.getSettlementDetails().getRawPayloadJson());
        settlementDetails.setUpdatedAt(now);
        dataManager.save(settlementDetails);
    }

    private Event upsertEvent(final ImportedEventData eventData, final Organiser organiser) {
        Event event = dataManager.load(Event.class)
                .query("e.megatixEventId = ?1", eventData.getMegatixEventId())
                .optional()
                .orElseGet(() -> dataManager.create(Event.class));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (event.getCreatedAt() == null) {
            event.setCreatedAt(now);
        }
        event.setMegatixEventId(eventData.getMegatixEventId());
        event.setOrganiser(organiser);
        event.setTitle(eventData.getTitle());
        event.setSlug(eventData.getSlug());
        event.setDescription(eventData.getDescription());
        event.setVenueName(eventData.getVenueName());
        event.setVenueJson(eventData.getVenueJson());
        event.setPhotosJson(eventData.getPhotosJson());
        event.setEventStartAt(eventData.getEventStartAt());
        event.setEventEndAt(eventData.getEventEndAt());
        event.setTimezoneName(eventData.getTimezoneName());
        event.setRawPayloadJson(eventData.getRawPayloadJson());
        event.setUpdatedAt(now);
        return dataManager.save(event);
    }

    private void syncTicketTiers(final Event event, final MegatixEventDetailsResponse eventResponse) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String eventCurrencyCode = eventResponse.payload().getCurrencyCode();
        List<EventTicketTier> existingTiers = dataManager.load(EventTicketTier.class)
                .query("e.event.id = ?1", event.getId())
                .list();
        Map<String, EventTicketTier> existingByMegatixId = new HashMap<>();
        for (EventTicketTier tier : existingTiers) {
            existingByMegatixId.put(tier.getMegatixTierId(), tier);
        }

        Set<String> seenMegatixTierIds = new HashSet<>();
        for (var ticket : eventResponse.payload().getTickets()) {
            ImportedTicketTierData tierData = megatixImportMapper.toImportedTicketTierData(ticket, serialize(ticket));
            if (tierData.getCurrencyCode() == null || tierData.getCurrencyCode().isBlank()) {
                tierData.setCurrencyCode(eventCurrencyCode);
            }
            requireNonBlank(tierData.getMegatixTierId(), "Megatix tier ID is required");
            requireNonBlank(tierData.getName(), "Megatix tier name is required");
            requireNonBlank(tierData.getCurrencyCode(), "Megatix tier currency is required");

            seenMegatixTierIds.add(tierData.getMegatixTierId());

            EventTicketTier tier = existingByMegatixId.getOrDefault(tierData.getMegatixTierId(),
                    dataManager.create(EventTicketTier.class));
            if (tier.getCreatedAt() == null) {
                tier.setCreatedAt(now);
            }
            tier.setEvent(event);
            tier.setMegatixTierId(tierData.getMegatixTierId());
            tier.setName(tierData.getName());
            tier.setDescription(tierData.getDescription());
            tier.setCurrencyCode(tierData.getCurrencyCode());
            tier.setFacePrice(tierData.getFacePrice());
            tier.setAvailabilityCount(tierData.getAvailabilityCount());
            tier.setAvailabilityState(tierData.getAvailabilityState());
            tier.setSalesStartsAt(tierData.getSalesStartsAt());
            tier.setSalesEndsAt(tierData.getSalesEndsAt());
            tier.setDisplayOrder(tierData.getDisplayOrder());
            tier.setIsActive(Boolean.TRUE.equals(tierData.getActive()));
            tier.setLastAvailabilitySyncAt(now);
            tier.setRawPayloadJson(tierData.getRawPayloadJson());
            tier.setUpdatedAt(now);
            existingByMegatixId.put(tierData.getMegatixTierId(), tier);
        }

        for (EventTicketTier tier : existingTiers) {
            if (seenMegatixTierIds.contains(tier.getMegatixTierId())) {
                continue;
            }
            tier.setIsActive(false);
            tier.setUpdatedAt(now);
        }

        SaveContext saveContext = new SaveContext();
        for (EventTicketTier tier : existingByMegatixId.values()) {
            saveContext.saving(tier);
        }
        dataManager.save(saveContext);
    }

    private void requireNonBlank(final String value, final String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }

    private String serialize(final Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Megatix payload", e);
        }
    }
}
