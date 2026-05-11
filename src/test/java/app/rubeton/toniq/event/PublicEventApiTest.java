package app.rubeton.toniq.event;

import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.EventPublicationSettings;
import app.rubeton.toniq.entity.EventStatusOverride;
import app.rubeton.toniq.entity.EventSyncState;
import app.rubeton.toniq.entity.EventTicketTier;
import app.rubeton.toniq.entity.Organiser;
import app.rubeton.toniq.entity.OverrideStatus;
import app.rubeton.toniq.entity.PublicationMode;
import app.rubeton.toniq.entity.TierAvailabilityState;
import app.rubeton.toniq.service.EventPublicationService;
import app.rubeton.toniq.service.EventStatusService;
import app.rubeton.toniq.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class PublicEventApiTest {

    @Autowired
    DataManager dataManager;

    @Autowired
    EventPublicationService eventPublicationService;

    @Autowired
    EventStatusService eventStatusService;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SystemAuthenticator systemAuthenticator;

    @AfterEach
    void tearDown() {
        systemAuthenticator.runWithSystem(() -> {
            dataManager.load(EventStatusOverride.class)
                    .query("e.event.megatixEventId like ?1", "public-event-%")
                    .list()
                    .forEach(dataManager::remove);
            dataManager.load(EventTicketTier.class)
                    .query("e.event.megatixEventId like ?1", "public-event-%")
                    .list()
                    .forEach(dataManager::remove);
            dataManager.load(EventPublicationSettings.class)
                    .query("e.event.megatixEventId like ?1", "public-event-%")
                    .list()
                    .forEach(dataManager::remove);
            dataManager.load(EventSyncState.class)
                    .query("e.event.megatixEventId like ?1", "public-event-%")
                    .list()
                    .forEach(dataManager::remove);
            dataManager.load(Event.class)
                    .query("e.megatixEventId like ?1", "public-event-%")
                    .list()
                    .forEach(dataManager::remove);
            dataManager.load(Organiser.class)
                    .query("e.megatixOrganiserId like ?1", "public-org-%")
                    .list()
                    .forEach(dataManager::remove);
        });
    }

    @Test
    void slugEndpointReturnsPublishedEventDetailPayload() throws Exception {
        String suffix = uniqueSuffix();
        String slug = "art-after-dark-" + suffix;
        Organiser organiser = saveOrganiser(suffix);
        Event event = saveEvent(organiser, suffix, slug);
        event.setDescription("""
                <p onclick="alert('xss')">Night show <strong>live</strong></p>
                <script>alert('boom')</script>
                <a href="https://example.com">link</a>
                """);
        event.setVenueName("Main Hall");
        event.setVenueJson("""
                {"full_address":"221B Baker Street","suburb":"Central","country_code":"GB"}
                """);
        event.setPhotosJson("""
                ["https://cdn.example.com/hero.jpg","https://cdn.example.com/second.jpg"]
                """);
        event.setEventStartAt(OffsetDateTime.parse("2026-06-11T18:30:00Z"));
        event.setEventEndAt(OffsetDateTime.parse("2026-06-11T22:30:00Z"));
        event.setTimezoneName("Europe/London");
        event.setRawPayloadJson("{\"projection\":true}");
        event = dataManager.save(event);

        eventPublicationService.recordMegatixWebhookState(event, true, "megatix_enabled", nowUtc());
        eventStatusService.applyOverride(event, OverrideStatus.RESCHEDULED,
                OffsetDateTime.parse("2026-06-12T18:30:00Z"),
                OffsetDateTime.parse("2026-06-12T22:30:00Z"),
                "new date",
                "ops");

        saveTier(event, suffix + "-vip", "VIP", true, 2, new BigDecimal("89.00"), TierAvailabilityState.LOW);
        saveTier(event, suffix + "-general", "General", true, 1, new BigDecimal("39.00"), TierAvailabilityState.AVAILABLE);
        saveTier(event, suffix + "-hidden", "Hidden", false, 3, new BigDecimal("19.00"), TierAvailabilityState.SOLD_OUT);

        mockMvc.perform(get("/api/public/events/by-slug/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.slug").value(slug))
                .andExpect(jsonPath("$.event.id").value(event.getId().toString()))
                .andExpect(jsonPath("$.event.megatixEventId").value(event.getMegatixEventId()))
                .andExpect(jsonPath("$.event.organiserName").value("Public Organiser " + suffix))
                .andExpect(jsonPath("$.event.heroImageUrl").value("https://cdn.example.com/hero.jpg"))
                .andExpect(jsonPath("$.event.galleryImageUrls.length()").value(2))
                .andExpect(jsonPath("$.event.descriptionHtml").value(org.hamcrest.Matchers.containsString("<strong>live</strong>")))
                .andExpect(jsonPath("$.event.descriptionHtml").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script"))))
                .andExpect(jsonPath("$.venue.name").value("Main Hall"))
                .andExpect(jsonPath("$.venue.address").value("221B Baker Street"))
                .andExpect(jsonPath("$.status.effectiveStatus").value("rescheduled"))
                .andExpect(jsonPath("$.status.statusLabel").value("Rescheduled"))
                .andExpect(jsonPath("$.status.rescheduledStartAt").isNotEmpty())
                .andExpect(jsonPath("$.availability.lastUpdatedAt").isNotEmpty())
                .andExpect(jsonPath("$.availability.source").value("stored_snapshot"))
                .andExpect(jsonPath("$.tickets.length()").value(2))
                .andExpect(jsonPath("$.tickets[0].name").value("General"))
                .andExpect(jsonPath("$.tickets[1].name").value("VIP"))
                .andExpect(jsonPath("$.cta.kind").value("placeholder"));
    }

    @Test
    void idEndpointReturnsPublishedEventWithoutSlug() throws Exception {
        String suffix = uniqueSuffix();
        Organiser organiser = saveOrganiser(suffix);
        Event event = saveEvent(organiser, suffix, null);
        event.setRawPayloadJson("{\"projection\":true}");
        event = dataManager.save(event);
        eventPublicationService.setPublicationMode(event, PublicationMode.ON, "admin_mode_on", nowUtc());

        mockMvc.perform(get("/api/public/events/{id}", event.getMegatixEventId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.id").value(event.getId().toString()))
                .andExpect(jsonPath("$.event.megatixEventId").value(event.getMegatixEventId()))
                .andExpect(jsonPath("$.event.slug").doesNotExist())
                .andExpect(jsonPath("$.availability.source").value("stored_snapshot"))
                .andExpect(jsonPath("$.status.effectiveStatus").value("active"));
    }

    @Test
    void hiddenEventsReturnNotFoundAcrossEndpoints() throws Exception {
        String suffix = uniqueSuffix();
        Organiser organiser = saveOrganiser(suffix);
        Event unpublished = saveEvent(organiser, suffix + "-off", "draft-event");
        unpublished.setRawPayloadJson("{\"projection\":true}");
        unpublished = dataManager.save(unpublished);
        eventPublicationService.setPublicationMode(unpublished, PublicationMode.OFF, "admin_mode_off", nowUtc());

        Event missingProjection = saveEvent(organiser, suffix + "-missing", "projection-missing");
        eventPublicationService.setPublicationMode(missingProjection, PublicationMode.ON, "admin_mode_on", nowUtc());

        mockMvc.perform(get("/api/public/events/by-slug/{slug}", "draft-event"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/public/events/{id}", unpublished.getMegatixEventId()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/public/events/by-slug/{slug}", "projection-missing"))
                .andExpect(status().isNotFound());
    }

    private Organiser saveOrganiser(final String suffix) {
        Organiser organiser = dataManager.create(Organiser.class);
        organiser.setMegatixOrganiserId("public-org-" + suffix);
        organiser.setName("Public Organiser " + suffix);
        organiser.setEmail("public+" + suffix + "@example.com");
        organiser.setCreatedAt(nowUtc());
        organiser.setUpdatedAt(nowUtc());
        return dataManager.save(organiser);
    }

    private Event saveEvent(final Organiser organiser, final String suffix, final String slug) {
        Event event = dataManager.create(Event.class);
        event.setMegatixEventId("public-event-" + suffix);
        event.setOrganiser(organiser);
        event.setTitle("Public Event " + suffix);
        event.setSlug(slug);
        event.setCreatedAt(nowUtc());
        event.setUpdatedAt(nowUtc());
        return dataManager.save(event);
    }

    private EventTicketTier saveTier(final Event event,
                                     final String suffix,
                                     final String name,
                                     final boolean active,
                                     final int displayOrder,
                                     final BigDecimal price,
                                     final TierAvailabilityState state) {
        EventTicketTier tier = dataManager.create(EventTicketTier.class);
        tier.setEvent(event);
        tier.setMegatixTierId("public-tier-" + suffix);
        tier.setName(name);
        tier.setDescription(name + " access");
        tier.setCurrencyCode("EUR");
        tier.setFacePrice(price);
        tier.setAvailabilityCount(active ? 12 : 0);
        tier.setAvailabilityState(state);
        tier.setSalesStartsAt(nowUtc().minusDays(1));
        tier.setSalesEndsAt(nowUtc().plusDays(7));
        tier.setDisplayOrder(displayOrder);
        tier.setIsActive(active);
        tier.setLastAvailabilitySyncAt(nowUtc());
        tier.setCreatedAt(nowUtc());
        tier.setUpdatedAt(nowUtc());
        return dataManager.save(tier);
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String uniqueSuffix() {
        return String.valueOf(System.currentTimeMillis());
    }
}
