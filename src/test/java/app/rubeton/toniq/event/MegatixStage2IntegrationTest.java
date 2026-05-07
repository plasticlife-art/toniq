package app.rubeton.toniq.event;

import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.EventPublicationSettings;
import app.rubeton.toniq.entity.EventSyncLog;
import app.rubeton.toniq.entity.EventSyncState;
import app.rubeton.toniq.entity.EventTicketTier;
import app.rubeton.toniq.entity.Organiser;
import app.rubeton.toniq.entity.OrganiserSettlementDetails;
import app.rubeton.toniq.entity.PublicationState;
import app.rubeton.toniq.entity.SyncLogStatus;
import app.rubeton.toniq.entity.SyncLogScope;
import app.rubeton.toniq.entity.SyncLogTriggerSource;
import app.rubeton.toniq.entity.SyncResult;
import app.rubeton.toniq.service.MegatixClient;
import app.rubeton.toniq.service.megatix.ManualSyncStatusService;
import app.rubeton.toniq.service.megatix.MegatixAuthService;
import app.rubeton.toniq.service.megatix.MegatixSyncCoordinator;
import app.rubeton.toniq.service.megatix.impl.MegatixSyncCoordinatorImpl;
import app.rubeton.toniq.service.megatix.model.ManualSyncHandle;
import app.rubeton.toniq.service.megatix.model.ManualSyncStatus;
import app.rubeton.toniq.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class MegatixStage2IntegrationTest {

    private static final MockWebServer MEGATIX_SERVER;

    static {
        try {
            MEGATIX_SERVER = new MockWebServer();
            MEGATIX_SERVER.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Autowired
    DataManager dataManager;

    @Autowired
    MegatixClient megatixClient;

    @Autowired
    MegatixAuthService megatixAuthService;

    @Autowired
    MegatixSyncCoordinator megatixSyncCoordinator;

    @Autowired
    MegatixSyncCoordinatorImpl megatixSyncCoordinatorImpl;

    @Autowired
    ManualSyncStatusService manualSyncStatusService;

    @Autowired
    SystemAuthenticator systemAuthenticator;

    @Autowired
    MockMvc mockMvc;

    @DynamicPropertySource
    static void registerProperties(final DynamicPropertyRegistry registry) {
        registry.add("megatix.base-url", () -> MEGATIX_SERVER.url("/").toString());
        registry.add("megatix.email", () -> "ops@example.com");
        registry.add("megatix.password", () -> "secret");
        registry.add("megatix.webhook-signature", () -> "test-signature");
    }

    @BeforeAll
    static void beforeAll() {
        MEGATIX_SERVER.setDispatcher(new okhttp3.mockwebserver.QueueDispatcher());
    }

    @AfterAll
    static void afterAll() throws IOException {
        MEGATIX_SERVER.shutdown();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        megatixAuthService.evictAccessToken();
        MEGATIX_SERVER.setDispatcher(new okhttp3.mockwebserver.QueueDispatcher());
        clearRecordedRequests();
        systemAuthenticator.runWithSystem(() -> {
            dataManager.load(EventSyncLog.class).all().list().forEach(dataManager::remove);
            dataManager.load(EventTicketTier.class).all().list().forEach(dataManager::remove);
            dataManager.load(EventPublicationSettings.class).all().list().forEach(dataManager::remove);
            dataManager.load(EventSyncState.class).all().list().forEach(dataManager::remove);
            dataManager.load(Event.class).all().list().forEach(dataManager::remove);
            dataManager.load(OrganiserSettlementDetails.class).all().list().forEach(dataManager::remove);
            dataManager.load(Organiser.class).all().list().forEach(dataManager::remove);
        });
    }

    @Test
    void fetchesMegatixDataWithCachedTokenAndSingleLogin() throws Exception {
        enqueueJson(200, """
                {"token_type":"Bearer","access_token":"token-1","expires_in":3600}
                """);
        enqueueJson(200, buildEventJson("Event A", true));
        enqueueJson(200, """
                {"data":{"name":"Promoter A","description":null,"settlement_details":{"account_number":"1750000692680","account_bsb":"MANDIRI","account_name":"PT. Kharisma Anugrah Jawara Abadi","country_code":"ID","wallet_address":"0x123"}}}
                """);

        megatixClient.fetchEventDetails("evt-1");
        megatixClient.fetchEventPromoter("evt-1");

        RecordedRequest loginRequest = MEGATIX_SERVER.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest eventRequest = MEGATIX_SERVER.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest promoterRequest = MEGATIX_SERVER.takeRequest(1, TimeUnit.SECONDS);

        assertThat(loginRequest.getPath()).isEqualTo("/api/v3/accounts/login");
        assertThat(eventRequest.getPath()).isEqualTo("/api/v3/events/evt-1?is_ota=true");
        assertThat(promoterRequest.getPath()).isEqualTo("/api/v3/events/evt-1/promoter-ota");
        assertThat(eventRequest.getHeader("Authorization")).isEqualTo("Bearer token-1");
        assertThat(promoterRequest.getHeader("Authorization")).isEqualTo("Bearer token-1");
    }

    @Test
    void retriesOnceAfterUnauthorizedMegatixResponse() throws Exception {
        enqueueJson(200, """
                {"token_type":"Bearer","access_token":"token-1","expires_in":3600}
                """);
        enqueueJson(401, """
                {"message":"expired"}
                """);
        enqueueJson(200, """
                {"token_type":"Bearer","access_token":"token-2","expires_in":3600}
                """);
        enqueueJson(200, buildEventJson("Event Retry", true));

        megatixClient.fetchEventDetails("evt-retry");

        RecordedRequest loginRequest1 = takeRequiredRequest();
        RecordedRequest eventRequest1 = takeRequiredRequest();
        RecordedRequest loginRequest2 = takeRequiredRequest();
        RecordedRequest eventRequest2 = takeRequiredRequest();

        assertThat(loginRequest1.getPath()).isEqualTo("/api/v3/accounts/login");
        assertThat(eventRequest1.getPath()).isEqualTo("/api/v3/events/evt-retry?is_ota=true");
        assertThat(loginRequest2.getPath()).isEqualTo("/api/v3/accounts/login");
        assertThat(eventRequest2.getPath()).isEqualTo("/api/v3/events/evt-retry?is_ota=true");
        assertThat(eventRequest2.getHeader("Authorization")).isEqualTo("Bearer token-2");
    }

    @Test
    void manualImportCreatesProjectionAndKeepsEventUnpublished() {
        enqueueHappyImport("evt-manual", "Manual Event", true);

        ManualSyncHandle handle = megatixSyncCoordinator.submitManualImport("evt-manual");
        assertThat(handle.getSyncLogId()).isNotNull();

        ManualSyncStatus startedStatus = manualSyncStatusService.getStatus(handle.getSyncLogId());
        assertThat(startedStatus.getSyncLogId()).isEqualTo(handle.getSyncLogId());
        assertThat(startedStatus.getMegatixEventId()).isEqualTo("evt-manual");
        assertThat(startedStatus.getStatus()).isEqualTo(SyncLogStatus.STARTED);

        waitUntilNotNull(() -> {
            ManualSyncStatus status = manualSyncStatusService.getStatus(handle.getSyncLogId());
            return status.getStatus() == SyncLogStatus.SUCCESS ? status : null;
        });

        ManualSyncStatus completedStatus = manualSyncStatusService.getStatus(handle.getSyncLogId());
        assertThat(completedStatus.getStatus()).isEqualTo(SyncLogStatus.SUCCESS);
        assertThat(completedStatus.getFinishedAt()).isNotNull();

        Event event = findEvent("evt-manual");
        Organiser organiser = loadOrganiser(event);
        OrganiserSettlementDetails settlementDetails = loadSettlementDetails(organiser);
        EventPublicationSettings publication = loadPublication(event);
        EventSyncState syncState = loadSyncState(event);
        List<EventTicketTier> tiers = loadTiers(event);

        assertThat(organiser.getMegatixOrganiserId()).isEqualTo("998");
        assertThat(settlementDetails.getAccountNumber()).isEqualTo("1750000692680");
        assertThat(settlementDetails.getAccountBsb()).isEqualTo("MANDIRI");
        assertThat(settlementDetails.getAccountName()).isEqualTo("PT. Kharisma Anugrah Jawara Abadi");
        assertThat(settlementDetails.getCountryCode()).isEqualTo("ID");
        assertThat(settlementDetails.getWalletAddress()).isEqualTo("0x123");
        assertThat(settlementDetails.getRawPayloadJson()).contains("\"walletAddress\":\"0x123\"");
        assertThat(settlementDetails.getRawPayloadJson()).contains("\"accountNumber\":\"1750000692680\"");
        assertThat(publication.getCryptoEnabled()).isFalse();
        assertThat(publication.getPublished()).isFalse();
        assertThat(publication.getPublicationState()).isEqualTo(PublicationState.DRAFT);
        assertThat(syncState.getLastSyncResult()).isEqualTo(SyncResult.SUCCESS);
        assertThat(syncState.getLastEventDataSyncAt()).isNotNull();
        assertThat(syncState.getLastAvailabilitySyncAt()).isNotNull();
        assertThat(tiers).hasSize(2);
        assertThat(tiers).allMatch(EventTicketTier::getIsActive);
        assertThat(tiers).extracting(EventTicketTier::getCurrencyCode).containsOnly("EUR");
    }

    @Test
    void repeatedManualImportOfSameEventUpdatesExistingTiersWithoutOptimisticLockFailure() {
        enqueueHappyImport("evt-repeat", "Repeat Event", true);

        ManualSyncHandle firstHandle = megatixSyncCoordinator.submitManualImport("evt-repeat");
        waitUntilNotNull(() -> {
            ManualSyncStatus status = manualSyncStatusService.getStatus(firstHandle.getSyncLogId());
            return status.getStatus() == SyncLogStatus.SUCCESS ? status : null;
        });

        enqueueJson(200, buildEventJson("Repeat Event Updated", true).replace("evt-1", "evt-repeat"));
        enqueueJson(200, """
                {"data":{"name":"Promoter A","description":null,"settlement_details":{"account_number":"1750000692680","account_bsb":"MANDIRI","account_name":"PT. Kharisma Anugrah Jawara Abadi","country_code":"ID","wallet_address":"0x123"}}}
                """);
        ManualSyncHandle secondHandle = megatixSyncCoordinator.submitManualImport("evt-repeat");
        waitUntilNotNull(() -> {
            ManualSyncStatus status = manualSyncStatusService.getStatus(secondHandle.getSyncLogId());
            return status.getStatus() == SyncLogStatus.SUCCESS ? status : null;
        });

        Event event = findEvent("evt-repeat");
        List<EventTicketTier> tiers = loadTiers(event);

        assertThat(event.getTitle()).isEqualTo("Repeat Event Updated");
        assertThat(tiers).hasSize(2);
        assertThat(tiers).allMatch(EventTicketTier::getIsActive);
        assertThat(latestSyncLog("evt-repeat").getStatus()).isEqualTo(SyncLogStatus.SUCCESS);
    }

    @Test
    void webhookEnableImportsAndPublishesEvent() throws Exception {
        enqueueHappyImport("evt-webhook", "Webhook Event", true);

        mockMvc.perform(post("/api/megatix/webhook")
                        .header("x-signature", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"event":"event.ota.status.updated","data":{"event_id":"evt-webhook","enabled":true}}
                                """))
                .andExpect(status().isAccepted());

        waitUntilNotNull(() -> {
            EventSyncLog log = latestSyncLog("evt-webhook");
            return log != null && log.getStatus() == SyncLogStatus.SUCCESS ? log : null;
        });
        Event event = findEvent("evt-webhook");
        EventPublicationSettings publication = loadPublication(event);
        EventSyncLog syncLog = latestSyncLog("evt-webhook");

        assertThat(publication.getCryptoEnabled()).isTrue();
        assertThat(publication.getPublished()).isTrue();
        assertThat(publication.getPublicationState()).isEqualTo(PublicationState.PUBLISHED);
        assertThat(syncLog.getTriggerSource()).isEqualTo(SyncLogTriggerSource.WEBHOOK_ENABLE);
        assertThat(syncLog.getStatus()).isEqualTo(SyncLogStatus.SUCCESS);
    }

    @Test
    void unsupportedWebhookEventIsLoggedAndPersistedAsIgnored() throws Exception {
        mockMvc.perform(post("/api/megatix/webhook")
                        .header("x-signature", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"event":"event.unknown","data":{"event_id":"evt-unsupported","enabled":true}}
                                """))
                .andExpect(status().isAccepted());

        EventSyncLog syncLog = waitUntilNotNull(() -> latestSyncLog("evt-unsupported"));
        assertThat(syncLog.getTriggerSource()).isEqualTo(SyncLogTriggerSource.WEBHOOK_UNSUPPORTED);
        assertThat(syncLog.getStatus()).isEqualTo(SyncLogStatus.IGNORED);
        assertThat(syncLog.getErrorCode()).isEqualTo("unsupported_webhook_event");
        assertThat(findEvent("evt-unsupported")).isNull();
    }

    @Test
    void repeatedImportRefreshesSameEventAndDeactivatesMissingTier() {
        enqueueHappyImport("evt-refresh", "Refresh Event", true);
        ManualSyncHandle importHandle = megatixSyncCoordinator.submitManualImport("evt-refresh");
        waitUntilNotNull(() -> {
            ManualSyncStatus status = manualSyncStatusService.getStatus(importHandle.getSyncLogId());
            return status.getStatus() == SyncLogStatus.SUCCESS ? status : null;
        });
        Event event = findEvent("evt-refresh");

        enqueueJson(200, buildEventJson("Refresh Event Updated", false));
        enqueueJson(200, """
                {"data":{"name":"Promoter A","description":null,"settlement_details":{"account_number":"1750000692680","account_bsb":"MANDIRI","account_name":"PT. Kharisma Anugrah Jawara Abadi","country_code":"ID","wallet_address":"0x123"}}}
                """);
        ManualSyncHandle resyncHandle = megatixSyncCoordinator.submitManualResync("evt-refresh");

        waitUntilNotNull(() -> {
            ManualSyncStatus status = manualSyncStatusService.getStatus(resyncHandle.getSyncLogId());
            if (status.getStatus() == SyncLogStatus.SUCCESS) {
                EventSyncLog log = loadSyncLog(status.getSyncLogId());
                if (log.getTriggerSource() == SyncLogTriggerSource.MANUAL
                        && log.getSyncScope() == SyncLogScope.EVENT_REFRESH) {
                    return status;
                }
            }
            return null;
        });
        Event refreshedEvent = findEvent("evt-refresh");
        List<EventTicketTier> tiers = loadTiers(refreshedEvent);

        assertThat(tiers).hasSize(2);
        assertThat(tiers).extracting(EventTicketTier::getMegatixTierId)
                .containsExactlyInAnyOrder("tier-1", "tier-2");
    }

    @Test
    void manualImportParsesMegatixLocalDateTimeFormatUsingVenueTimezone() {
        enqueueJson(200, """
                {"token_type":"Bearer","access_token":"token-1","expires_in":3600}
                """);
        enqueueJson(200, """
                {
                  "data":{
                    "id":"evt-local-time",
                    "promoter_id":"998",
                    "name":"Local Time Event",
                    "slug":"local-time-event",
                    "description":"Imported event description",
                    "start_datetime":"2026-12-31 00:00:00",
                    "end_datetime":"2027-01-01 03:00:00",
                    "cover":"",
                    "venue":{"name":"Ad Lib Bangkok","timezone":"Australia/Perth"},
                    "tickets":[]
                  },
                  "meta":{"locales":{"0":"en"}}
                }
                """);
        enqueueJson(200, """
                {"data":{"name":"Promoter A","description":null,"settlement_details":{"account_number":"1750000692680","account_bsb":"MANDIRI","account_name":"PT. Kharisma Anugrah Jawara Abadi","country_code":"ID","wallet_address":"0x123"}}}
                """);

        ManualSyncHandle handle = megatixSyncCoordinator.submitManualImport("evt-local-time");

        waitUntilNotNull(() -> {
            ManualSyncStatus status = manualSyncStatusService.getStatus(handle.getSyncLogId());
            return status.getStatus() == SyncLogStatus.SUCCESS ? status : null;
        });

        Event event = findEvent("evt-local-time");
        assertThat(event.getTimezoneName()).isEqualTo("Australia/Perth");
        assertThat(event.getEventStartAt()).isEqualTo(OffsetDateTime.parse("2026-12-31T00:00:00+08:00"));
        assertThat(event.getEventEndAt()).isEqualTo(OffsetDateTime.parse("2027-01-01T03:00:00+08:00"));
    }

    @Test
    void manualImportFallsBackToEventCurrencyWhenTicketCurrencyMissing() {
        enqueueJson(200, """
                {"token_type":"Bearer","access_token":"token-1","expires_in":3600}
                """);
        enqueueJson(200, """
                {
                  "data":{
                    "id":"evt-tier-currency-fallback",
                    "promoter_id":"998",
                    "name":"Currency Fallback Event",
                    "slug":"currency-fallback-event",
                    "description":"Imported event description",
                    "currency_code_iso4217":"THB",
                    "start_datetime":"2026-12-31T00:00:00Z",
                    "end_datetime":"2027-01-01T03:00:00Z",
                    "venue":{"name":"Ad Lib Bangkok","timezone":"Australia/Perth"},
                    "tickets":[
                      {
                        "id":"tier-1",
                        "name":"Early Bird",
                        "price":100000,
                        "display_order":1,
                        "free_seats_count":0,
                        "is_sold_out":false
                      }
                    ]
                  },
                  "meta":{"locales":{"0":"en"}}
                }
                """);
        enqueueJson(200, """
                {"data":{"name":"Promoter A","description":null,"settlement_details":{"account_number":"1750000692680","account_bsb":"MANDIRI","account_name":"PT. Kharisma Anugrah Jawara Abadi","country_code":"ID","wallet_address":"0x123"}}}
                """);

        ManualSyncHandle handle = megatixSyncCoordinator.submitManualImport("evt-tier-currency-fallback");

        waitUntilNotNull(() -> {
            ManualSyncStatus status = manualSyncStatusService.getStatus(handle.getSyncLogId());
            return status.getStatus() == SyncLogStatus.SUCCESS ? status : null;
        });

        Event event = findEvent("evt-tier-currency-fallback");
        List<EventTicketTier> tiers = loadTiers(event);
        assertThat(tiers).hasSize(1);
        assertThat(tiers.get(0).getCurrencyCode()).isEqualTo("THB");
    }

    @Test
    void manualSyncEvictsLockEntriesAfterCompletionAndReuse() {
        enqueueHappyImport("evt-lock-cleanup", "Lock Cleanup Event", true);

        ManualSyncHandle importHandle = megatixSyncCoordinator.submitManualImport("evt-lock-cleanup");
        waitUntilNotNull(() -> {
            ManualSyncStatus status = manualSyncStatusService.getStatus(importHandle.getSyncLogId());
            return status.getStatus() == SyncLogStatus.SUCCESS ? status : null;
        });
        waitUntilNotNull(() -> currentLockCount() == 0 ? Boolean.TRUE : null);

        enqueueJson(200, buildEventJson("Lock Cleanup Event Updated", false).replace("evt-1", "evt-lock-cleanup"));
        enqueueJson(200, """
                {"data":{"name":"Promoter A","description":null,"settlement_details":{"account_number":"1750000692680","account_bsb":"MANDIRI","account_name":"PT. Kharisma Anugrah Jawara Abadi","country_code":"ID","wallet_address":"0x123"}}}
                """);

        ManualSyncHandle resyncHandle = megatixSyncCoordinator.submitManualResync("evt-lock-cleanup");
        waitUntilNotNull(() -> {
            ManualSyncStatus status = manualSyncStatusService.getStatus(resyncHandle.getSyncLogId());
            return status.getStatus() == SyncLogStatus.SUCCESS ? status : null;
        });
        waitUntilNotNull(() -> currentLockCount() == 0 ? Boolean.TRUE : null);

        assertThat(currentLockCount()).isZero();
    }

    @Test
    void concurrentManualSyncForSameEventIsIgnoredAndLockEntryIsEvicted() throws Exception {
        CountDownLatch eventRequestStarted = new CountDownLatch(1);
        CountDownLatch allowFirstSyncToFinish = new CountDownLatch(1);
        MEGATIX_SERVER.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(final RecordedRequest request) throws InterruptedException {
                String path = request.getPath();
                if ("/api/v3/accounts/login".equals(path)) {
                    return jsonResponse(200, """
                            {"token_type":"Bearer","access_token":"token-1","expires_in":3600}
                            """);
                }
                if ("/api/v3/events/evt-lock-overlap?is_ota=true".equals(path)) {
                    eventRequestStarted.countDown();
                    allowFirstSyncToFinish.await(2, TimeUnit.SECONDS);
                    return jsonResponse(200, buildEventJson("Lock Overlap Event", true).replace("evt-1", "evt-lock-overlap"));
                }
                if ("/api/v3/events/evt-lock-overlap/promoter-ota".equals(path)) {
                    return jsonResponse(200, """
                            {"data":{"name":"Promoter A","description":null,"settlement_details":{"account_number":"1750000692680","account_bsb":"MANDIRI","account_name":"PT. Kharisma Anugrah Jawara Abadi","country_code":"ID","wallet_address":"0x123"}}}
                            """);
                }
                return jsonResponse(404, "{\"message\":\"unexpected\"}");
            }
        });

        ManualSyncHandle firstHandle = megatixSyncCoordinator.submitManualImport("evt-lock-overlap");
        assertThat(eventRequestStarted.await(2, TimeUnit.SECONDS)).isTrue();
        waitUntilNotNull(() -> currentLockCount() == 1 ? Boolean.TRUE : null);

        ManualSyncHandle secondHandle = megatixSyncCoordinator.submitManualImport("evt-lock-overlap");
        waitUntilNotNull(() -> {
            ManualSyncStatus status = manualSyncStatusService.getStatus(secondHandle.getSyncLogId());
            return status.getStatus() == SyncLogStatus.IGNORED ? status : null;
        });

        allowFirstSyncToFinish.countDown();

        waitUntilNotNull(() -> {
            ManualSyncStatus status = manualSyncStatusService.getStatus(firstHandle.getSyncLogId());
            return status.getStatus() == SyncLogStatus.SUCCESS ? status : null;
        });
        waitUntilNotNull(() -> currentLockCount() == 0 ? Boolean.TRUE : null);

        EventSyncLog ignoredLog = loadSyncLog(secondHandle.getSyncLogId());
        assertThat(ignoredLog.getStatus()).isEqualTo(SyncLogStatus.IGNORED);
        assertThat(ignoredLog.getErrorCode()).isEqualTo("sync_in_progress");
        assertThat(currentLockCount()).isZero();
    }

    @Test
    void multipleUniqueEventImportsDoNotAccumulateLockEntries() {
        List<String> eventIds = List.of("evt-lock-1", "evt-lock-2", "evt-lock-3");
        for (String eventId : eventIds) {
            enqueueHappyImport(eventId, "Lock Event " + eventId, true);
            ManualSyncHandle handle = megatixSyncCoordinator.submitManualImport(eventId);
            waitUntilNotNull(() -> {
                ManualSyncStatus status = manualSyncStatusService.getStatus(handle.getSyncLogId());
                return status.getStatus() == SyncLogStatus.SUCCESS ? status : null;
            });
            waitUntilNotNull(() -> currentLockCount() == 0 ? Boolean.TRUE : null);
        }

        assertThat(currentLockCount()).isZero();
    }

    private void enqueueHappyImport(final String eventId, final String title, final boolean includeSecondTier) {
        enqueueJson(200, """
                {"token_type":"Bearer","access_token":"token-1","expires_in":3600}
                """);
        enqueueJson(200, buildEventJson(title, includeSecondTier).replace("evt-1", eventId));
        enqueueJson(200, """
                {"data":{"name":"Promoter A","description":null,"settlement_details":{"account_number":"1750000692680","account_bsb":"MANDIRI","account_name":"PT. Kharisma Anugrah Jawara Abadi","country_code":"ID","wallet_address":"0x123"}}}
                """);
    }

    private void enqueueJson(final int status, final String body) {
        MEGATIX_SERVER.enqueue(new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body));
    }

    private MockResponse jsonResponse(final int status, final String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private String buildEventJson(final String title, final boolean includeSecondTier) {
        String secondTier = includeSecondTier ? """
                ,{
                  "id":"tier-2",
                  "name":"Second Tier",
                  "description":"Second tier",
                  "currency":"EUR",
                  "price":35.00,
                  "display_order":2,
                  "free_seats_count":0,
                  "is_sold_out":true
                }
                """ : "";
        return """
                {
                  "data":{
                    "id":"evt-1",
                    "promoter_id":"998",
                    "name":"%s",
                    "slug":"event-slug",
                    "description":"Imported event description",
                    "currency_code_iso4217":"THB",
                    "start_datetime":"2026-05-06T18:00:00Z",
                    "end_datetime":"2026-05-06T21:00:00Z",
                    "cover":"https://cdn.example.com/photo.jpg",
                    "venue":{"name":"Main Hall","city":"Podgorica","timezone":"UTC"},
                    "tickets":[
                      {
                        "id":"tier-1",
                        "name":"First Tier",
                        "description":"First tier",
                        "currency":"EUR",
                        "price":20.00,
                        "display_order":1,
                        "free_seats_count":25,
                        "is_sold_out":false
                      }%s
                    ]
                  },
                  "meta":{"locales":{"0":"en"}}
                }
                """.formatted(title, secondTier);
    }

    private Event findEvent(final String megatixEventId) {
        return systemAuthenticator.withSystem(() -> dataManager.load(Event.class)
                .query("e.megatixEventId = ?1", megatixEventId)
                .optional()
                .orElse(null));
    }

    private EventSyncLog latestSyncLog(final String megatixEventId) {
        List<EventSyncLog> logs = systemAuthenticator.withSystem(() -> dataManager.load(EventSyncLog.class)
                .query("e.megatixEventId = ?1", megatixEventId)
                .list());
        return logs.stream()
                .max(Comparator.comparing(EventSyncLog::getCreatedAt))
                .orElse(null);
    }

    private EventSyncLog loadSyncLog(final java.util.UUID syncLogId) {
        return systemAuthenticator.withSystem(() -> dataManager.load(EventSyncLog.class)
                .id(syncLogId)
                .one());
    }

    private EventPublicationSettings loadPublication(final Event event) {
        return systemAuthenticator.withSystem(() -> dataManager.load(EventPublicationSettings.class)
                .query("e.event = ?1", event)
                .one());
    }

    private Organiser loadOrganiser(final Event event) {
        return systemAuthenticator.withSystem(() -> dataManager.load(Organiser.class)
                .id(event.getOrganiser().getId())
                .one());
    }

    private OrganiserSettlementDetails loadSettlementDetails(final Organiser organiser) {
        return systemAuthenticator.withSystem(() -> dataManager.load(OrganiserSettlementDetails.class)
                .query("e.organiser = ?1", organiser)
                .one());
    }

    private EventSyncState loadSyncState(final Event event) {
        return systemAuthenticator.withSystem(() -> dataManager.load(EventSyncState.class)
                .query("e.event = ?1", event)
                .one());
    }

    private List<EventTicketTier> loadTiers(final Event event) {
        return systemAuthenticator.withSystem(() -> dataManager.load(EventTicketTier.class)
                .query("e.event.id = ?1 order by e.displayOrder", event.getId())
                .list());
    }

    private <T> T waitUntilNotNull(final Supplier<T> supplier) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        T value;
        do {
            value = supplier.get();
            if (value != null) {
                return value;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        } while (System.nanoTime() < deadline);
        return null;
    }

    private void clearRecordedRequests() throws InterruptedException {
        while (MEGATIX_SERVER.takeRequest(50, TimeUnit.MILLISECONDS) != null) {
            // drain queue between tests
        }
    }

    private RecordedRequest takeRequiredRequest() {
        try {
            RecordedRequest request = MEGATIX_SERVER.takeRequest(2, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            return request;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private int currentLockCount() {
        return (Integer) ReflectionTestUtils.invokeMethod(megatixSyncCoordinatorImpl, "activeEventLockCount");
    }
}
