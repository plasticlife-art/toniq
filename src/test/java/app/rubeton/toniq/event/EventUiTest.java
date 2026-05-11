package app.rubeton.toniq.event;

import app.rubeton.toniq.ToniqApplication;
import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.EventPublicationSettings;
import app.rubeton.toniq.entity.EventSyncLog;
import app.rubeton.toniq.entity.EventSyncState;
import app.rubeton.toniq.entity.EventTicketTier;
import app.rubeton.toniq.entity.Organiser;
import app.rubeton.toniq.entity.PublicationMode;
import app.rubeton.toniq.entity.SyncLogScope;
import app.rubeton.toniq.entity.SyncLogStatus;
import app.rubeton.toniq.entity.SyncLogTriggerSource;
import app.rubeton.toniq.entity.SyncResult;
import app.rubeton.toniq.entity.TierAvailabilityState;
import app.rubeton.toniq.view.event.EventDetailView;
import app.rubeton.toniq.view.event.EventListView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import io.jmix.core.DataManager;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.data.grid.DataGridItems;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.assertThat;

@UiTest
@SpringBootTest(classes = {ToniqApplication.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class EventUiTest {

    @Autowired
    DataManager dataManager;

    @Autowired
    ViewNavigators viewNavigators;

    @Test
    void test_eventDetailShowsAllTicketTiersSortedForInspection() {
        String suffix = uniqueSuffix();
        Organiser organiser = saveOrganiser(suffix);
        Event event = saveEvent(organiser, suffix);

        saveTier(event, suffix + "-vip", "VIP", 20, false, 2);
        saveTier(event, suffix + "-alpha", "Alpha", 10, true, 1);
        saveTier(event, suffix + "-beta", "beta", 5, true, 1);

        EventDetailView detailView = openEventDetail(event);
        DataGrid<EventTicketTier> ticketTiersDataGrid = UiTestUtils.getComponent(detailView, "ticketTiersDataGrid");

        DataGridItems<EventTicketTier> items = ticketTiersDataGrid.getItems();
        assertThat(items).isNotNull();
        assertThat(items.getItems())
                .extracting(EventTicketTier::getName)
                .containsExactly("Alpha", "beta", "VIP");
        assertThat(items.getItems())
                .extracting(EventTicketTier::getIsActive)
                .containsExactly(true, true, false);
    }

    @Test
    void test_eventDetailShowsEmptyStateWhenNoTicketTiersExist() {
        String suffix = uniqueSuffix();
        Organiser organiser = saveOrganiser(suffix);
        Event event = saveEvent(organiser, suffix);

        EventDetailView detailView = openEventDetail(event);
        DataGrid<EventTicketTier> ticketTiersDataGrid = UiTestUtils.getComponent(detailView, "ticketTiersDataGrid");

        DataGridItems<EventTicketTier> items = ticketTiersDataGrid.getItems();
        assertThat(items).isNotNull();
        assertThat(items.getItems()).isEmpty();
        assertThat(ticketTiersDataGrid.getEmptyStateText())
                .isEqualTo("No ticket tiers");
    }

    @Test
    void test_eventDetailSanitizesImportedDescriptionHtml() {
        String suffix = uniqueSuffix();
        Organiser organiser = saveOrganiser(suffix);
        Event event = saveEvent(organiser, suffix);
        event.setDescription("""
                <p onclick="alert('xss')">Intro <strong>bold</strong> <em>italics</em></p>
                <script>alert('boom')</script>
                <style>.x{display:none}</style>
                <img src="https://evil.example/x.png" onerror="alert(1)">
                <ul><li>One</li><li>Two</li></ul>
                <a href="https://example.com" target="_self" rel="nofollow" style="color:red">https</a>
                <a href="http://example.org">http</a>
                <a href="mailto:ops@example.com">mail</a>
                <a href="javascript:alert('owned')">bad</a>
                """);
        event = dataManager.save(event);

        EventDetailView detailView = openEventDetail(event);
        Div descriptionContainer = UiTestUtils.getComponent(detailView, "descriptionContainer");
        String renderedHtml = descriptionContainer.getElement().getProperty("innerHTML");
        Document document = Jsoup.parseBodyFragment(renderedHtml);

        assertThat(renderedHtml).contains("<strong>bold</strong>");
        assertThat(renderedHtml).contains("<em>italics</em>");
        assertThat(renderedHtml).contains("<ul><li>One</li><li>Two</li></ul>");
        assertThat(renderedHtml).doesNotContain("<script");
        assertThat(renderedHtml).doesNotContain("<style");
        assertThat(renderedHtml).doesNotContain("<img");
        assertThat(renderedHtml).doesNotContain("onclick=");
        assertThat(renderedHtml).doesNotContain("javascript:");

        Element httpsLink = document.selectFirst("a[href='https://example.com']");
        Element httpLink = document.selectFirst("a[href='http://example.org']");
        Element mailtoLink = document.selectFirst("a[href='mailto:ops@example.com']");
        Element badLink = document.selectFirst("a:containsOwn(bad)");

        assertThat(httpsLink).isNotNull();
        assertThat(httpLink).isNotNull();
        assertThat(mailtoLink).isNotNull();
        assertThat(httpsLink.attr("target")).isEqualTo("_blank");
        assertThat(httpLink.attr("target")).isEqualTo("_blank");
        assertThat(mailtoLink.attr("target")).isEqualTo("_blank");
        assertThat(httpsLink.attr("rel")).isEqualTo("noopener noreferrer");
        assertThat(httpLink.attr("rel")).isEqualTo("noopener noreferrer");
        assertThat(mailtoLink.attr("rel")).isEqualTo("noopener noreferrer");
        assertThat(badLink).isNotNull();
        assertThat(badLink.hasAttr("href")).isFalse();
        assertThat(badLink.hasAttr("target")).isFalse();
        assertThat(badLink.hasAttr("rel")).isFalse();
    }

    @Test
    void test_eventDetailShowsPublicationControlsAndPersistsMode() {
        String suffix = uniqueSuffix();
        Organiser organiser = saveOrganiser(suffix);
        Event event = saveEvent(organiser, suffix);

        EventDetailView detailView = openEventDetail(event);
        Div publicationControlContainer = UiTestUtils.getComponent(detailView, "publicationControlContainer");
        @SuppressWarnings("unchecked")
        RadioButtonGroup<PublicationMode> modeGroup = (RadioButtonGroup<PublicationMode>) publicationControlContainer.getChildren()
                .filter(component -> component instanceof RadioButtonGroup<?>)
                .findFirst()
                .orElseThrow();
        Button applyButton = UiTestUtils.getComponent(detailView, "applyPublicationModeButton");

        assertThat(modeGroup.getValue()).isEqualTo(PublicationMode.AUTO);
        assertThat(publicationControlContainer.getText()).doesNotContain("Event is not soft-deleted");
        assertThat(publicationControlContainer.getText()).doesNotContain("Crypto is enabled");
        assertThat(publicationControlContainer.getText()).doesNotContain("Publication flag is enabled");
        assertThat(publicationControlContainer.getText()).doesNotContain("Exists in local SROAST projection");

        modeGroup.setValue(PublicationMode.ON);
        applyButton.click();

        EventPublicationSettings settings = dataManager.load(EventPublicationSettings.class)
                .query("e.event = ?1", event)
                .one();
        assertThat(settings.getPublicationMode()).isEqualTo(PublicationMode.ON);
        assertThat(modeGroup.getValue()).isEqualTo(PublicationMode.ON);
    }

    @Test
    void test_eventListShowsSyncSummaryColumnsAndLoadedSyncState() {
        String suffix = uniqueSuffix();
        Organiser organiser = saveOrganiser(suffix);
        Event event = saveEvent(organiser, suffix);
        EventSyncState syncState = saveSyncState(event, SyncResult.SUCCESS,
                OffsetDateTime.parse("2026-05-11T10:15:00Z"),
                OffsetDateTime.parse("2026-05-11T10:15:00Z"),
                OffsetDateTime.parse("2026-05-11T10:11:00Z"));

        viewNavigators.view(UiTestUtils.getCurrentView(), EventListView.class).navigate();

        EventListView eventListView = UiTestUtils.getCurrentView();
        DataGrid<Event> eventsDataGrid = UiTestUtils.getComponent(eventListView, "eventsDataGrid");
        DataGridItems<Event> items = eventsDataGrid.getItems();

        assertThat(eventsDataGrid.getColumns())
                .extracting(column -> String.valueOf(column.getKey()))
                .contains("publicationSettings.cryptoEnabled", "publicationSettings.published",
                        "syncState.lastSyncedAt", "syncState.lastSyncResult", "syncState.lastAvailabilitySyncAt");
        assertThat(items.getItems())
                .extracting(Event::getMegatixEventId)
                .contains(event.getMegatixEventId());
        assertThat(items.getItems())
                .filteredOn(loadedEvent -> event.getMegatixEventId().equals(loadedEvent.getMegatixEventId()))
                .singleElement()
                .satisfies(loadedEvent -> {
                    assertThat(loadedEvent.getSyncState()).isNotNull();
                    assertThat(loadedEvent.getSyncState().getId()).isEqualTo(syncState.getId());
                    assertThat(loadedEvent.getSyncState().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS);
                });
    }

    @Test
    void test_eventDetailShowsRecentSyncLogsAndOpensDetailsDialog() {
        String suffix = uniqueSuffix();
        Organiser organiser = saveOrganiser(suffix);
        Event event = saveEvent(organiser, suffix);
        saveSyncLog(event, "older-payload", SyncLogStatus.FAILURE, SyncLogTriggerSource.POLLING,
                SyncLogScope.EVENT_REFRESH, OffsetDateTime.parse("2026-05-11T09:00:00Z"));
        saveSyncLog(event, "latest-payload", SyncLogStatus.SUCCESS, SyncLogTriggerSource.MANUAL,
                SyncLogScope.AVAILABILITY_REFRESH, OffsetDateTime.parse("2026-05-11T10:00:00Z"));

        EventDetailView detailView = openEventDetail(event);
        DataGrid<EventSyncLog> syncLogsDataGrid = UiTestUtils.getComponent(detailView, "syncLogsDataGrid");
        Button viewSyncLogDetailsButton = UiTestUtils.getComponent(detailView, "viewSyncLogDetailsButton");

        DataGridItems<EventSyncLog> items = syncLogsDataGrid.getItems();
        assertThat(items).isNotNull();
        assertThat(items.getItems())
                .extracting(log -> log.getRequestPayloadJson())
                .containsExactly("latest-payload", "older-payload");

        EventSyncLog selectedLog = items.getItems().iterator().next();
        syncLogsDataGrid.select(selectedLog);
        viewSyncLogDetailsButton.click();
    }

    @AfterEach
    void tearDown() {
        dataManager.load(EventSyncLog.class)
                .query("e.event.megatixEventId like ?1", "ui-event-%")
                .list()
                .forEach(dataManager::remove);
        dataManager.load(EventSyncState.class)
                .query("e.event.megatixEventId like ?1", "ui-event-%")
                .list()
                .forEach(dataManager::remove);
        dataManager.load(EventTicketTier.class)
                .query("e.megatixTierId like ?1", "ui-tier-%")
                .list()
                .forEach(dataManager::remove);
        dataManager.load(EventPublicationSettings.class)
                .query("e.event.megatixEventId like ?1", "ui-event-%")
                .list()
                .forEach(dataManager::remove);
        dataManager.load(Event.class)
                .query("e.megatixEventId like ?1", "ui-event-%")
                .list()
                .forEach(dataManager::remove);
        dataManager.load(Organiser.class)
                .query("e.megatixOrganiserId like ?1", "ui-org-%")
                .list()
                .forEach(dataManager::remove);
    }

    private EventDetailView openEventDetail(final Event event) {
        viewNavigators.view(UiTestUtils.getCurrentView(), EventListView.class).navigate();

        EventListView eventListView = UiTestUtils.getCurrentView();
        JmixButton editButton = UiTestUtils.getComponent(eventListView, "editButton");
        assertThat(editButton).isNotNull();

        viewNavigators.detailView(eventListView, Event.class)
                .editEntity(event)
                .withViewClass(EventDetailView.class)
                .navigate();

        return UiTestUtils.getCurrentView();
    }

    private Organiser saveOrganiser(final String suffix) {
        Organiser organiser = dataManager.create(Organiser.class);
        organiser.setMegatixOrganiserId("ui-org-" + suffix);
        organiser.setName("UI Organiser " + suffix);
        organiser.setEmail("ui+" + suffix + "@example.com");
        organiser.setCreatedAt(nowUtc());
        organiser.setUpdatedAt(nowUtc());
        return dataManager.save(organiser);
    }

    private Event saveEvent(final Organiser organiser, final String suffix) {
        Event event = dataManager.create(Event.class);
        event.setMegatixEventId("ui-event-" + suffix);
        event.setOrganiser(organiser);
        event.setTitle("UI Event " + suffix);
        event.setCreatedAt(nowUtc());
        event.setUpdatedAt(nowUtc());
        return dataManager.save(event);
    }

    private EventSyncState saveSyncState(final Event event,
                                         final SyncResult result,
                                         final OffsetDateTime lastSyncedAt,
                                         final OffsetDateTime lastEventDataSyncAt,
                                         final OffsetDateTime lastAvailabilitySyncAt) {
        EventSyncState syncState = dataManager.create(EventSyncState.class);
        syncState.setEvent(event);
        syncState.setLastSyncedAt(lastSyncedAt);
        syncState.setLastSyncResult(result);
        syncState.setLastEventDataSyncAt(lastEventDataSyncAt);
        syncState.setLastAvailabilitySyncAt(lastAvailabilitySyncAt);
        syncState.setCreatedAt(nowUtc());
        syncState.setUpdatedAt(nowUtc());
        return dataManager.save(syncState);
    }

    private EventSyncLog saveSyncLog(final Event event,
                                     final String payloadSeed,
                                     final SyncLogStatus status,
                                     final SyncLogTriggerSource triggerSource,
                                     final SyncLogScope syncScope,
                                     final OffsetDateTime startedAt) {
        EventSyncLog syncLog = dataManager.create(EventSyncLog.class);
        syncLog.setEvent(event);
        syncLog.setMegatixEventId(event.getMegatixEventId());
        syncLog.setTriggerSource(triggerSource);
        syncLog.setSyncScope(syncScope);
        syncLog.setStatus(status);
        syncLog.setRequestPayloadJson(payloadSeed);
        syncLog.setResponsePayloadJson("{\"seed\":\"" + payloadSeed + "\"}");
        syncLog.setErrorCode(status == SyncLogStatus.FAILURE ? "sample_error" : null);
        syncLog.setErrorMessage(status == SyncLogStatus.FAILURE ? "Sample failure for " + payloadSeed : null);
        syncLog.setStartedAt(startedAt);
        syncLog.setFinishedAt(startedAt.plusMinutes(2));
        syncLog.setCreatedAt(startedAt);
        return dataManager.save(syncLog);
    }

    private EventTicketTier saveTier(final Event event,
                                     final String suffix,
                                     final String name,
                                     final Integer availabilityCount,
                                     final boolean active,
                                     final int displayOrder) {
        EventTicketTier tier = dataManager.create(EventTicketTier.class);
        tier.setEvent(event);
        tier.setMegatixTierId("ui-tier-" + suffix);
        tier.setName(name);
        tier.setCurrencyCode("EUR");
        tier.setFacePrice(new BigDecimal("42.50"));
        tier.setAvailabilityCount(availabilityCount);
        tier.setAvailabilityState(active ? TierAvailabilityState.AVAILABLE : TierAvailabilityState.SOLD_OUT);
        tier.setSalesStartsAt(nowUtc().minusDays(1));
        tier.setSalesEndsAt(nowUtc().plusDays(1));
        tier.setDisplayOrder(displayOrder);
        tier.setIsActive(active);
        tier.setLastAvailabilitySyncAt(nowUtc());
        tier.setCreatedAt(nowUtc());
        tier.setUpdatedAt(nowUtc());
        return dataManager.save(tier);
    }

    private String uniqueSuffix() {
        return String.valueOf(System.currentTimeMillis());
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
