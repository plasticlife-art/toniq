package app.rubeton.toniq.view.event;

import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.EventPublicationSettings;
import app.rubeton.toniq.entity.EventTicketTier;
import app.rubeton.toniq.entity.Organiser;
import app.rubeton.toniq.entity.PublicationMode;
import app.rubeton.toniq.service.EventPublicationService;
import app.rubeton.toniq.service.PublicationDecision;
import app.rubeton.toniq.view.main.MainView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.router.Route;
import io.jmix.core.Messages;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.model.CollectionPropertyContainer;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.MessageBundle;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewDescriptor;
import io.jmix.flowui.view.Subscribe;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Route(value = "events/:id", layout = MainView.class)
@ViewController(id = "Event.detail")
@ViewDescriptor(path = "event-detail-view.xml")
@EditedEntityContainer("eventDc")
@DialogMode(width = "72em")
public class EventDetailView extends StandardDetailView<Event> {

    private static final Comparator<EventTicketTier> TICKET_TIER_ORDER =
            Comparator.comparing(EventTicketTier::getDisplayOrder, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(EventTicketTier::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    private static final Safelist DESCRIPTION_HTML_SAFELIST = Safelist.none()
            .addTags("p", "br", "strong", "b", "em", "i", "ul", "ol", "li", "a")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https", "mailto");

    @Autowired
    private UiComponents uiComponents;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventPublicationService eventPublicationService;

    @Autowired
    private Notifications notifications;

    @Autowired
    private Messages messages;

    @ViewComponent
    private TypedTextField<String> organiserNameField;

    @ViewComponent
    private MessageBundle messageBundle;

    @ViewComponent
    private Div descriptionContainer;

    @ViewComponent
    private Div venueContainer;

    @ViewComponent
    private Div photosContainer;

    @ViewComponent
    private Div publicationControlContainer;

    @ViewComponent
    private CollectionPropertyContainer<EventTicketTier> ticketTiersDc;

    private RadioButtonGroup<PublicationMode> publicationModeGroup;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        Event editedEvent = getEditedEntity();
        Organiser organiser = editedEvent != null ? editedEvent.getOrganiser() : null;
        organiserNameField.setValue(organiser != null && organiser.getName() != null ? organiser.getName() : "");
        descriptionContainer.getElement().setProperty("innerHTML", buildDescriptionMarkup(editedEvent));
        renderVenueDetails(editedEvent);
        renderPhotos(editedEvent);
        renderPublicationControls(editedEvent);
        sortTicketTiers();
    }

    private void renderPublicationControls(final Event event) {
        publicationControlContainer.removeAll();
        publicationControlContainer.addClassNames("event-detail-card", "event-detail-card--publication");
        publicationControlContainer.add(createSectionTitle(messageBundle.getMessage("publicationControlSectionTitle")));

        EventPublicationSettings settings = eventPublicationService.ensurePublicationSettings(event);
        PublicationDecision decision = eventPublicationService.getDecision(event);

        Span modeLabel = uiComponents.create(Span.class);
        modeLabel.setText(messageBundle.getMessage("publicationModeLabel") + ":");
        modeLabel.addClassName("publication-current-state-label");

        publicationModeGroup = new RadioButtonGroup<>();
        publicationModeGroup.setItems(PublicationMode.values());
        publicationModeGroup.setItemLabelGenerator(this::getPublicationModeLabel);
        publicationModeGroup.setValue(settings.getPublicationMode());
        publicationModeGroup.addClassName("publication-mode-group");

        Span currentStateLabel = uiComponents.create(Span.class);
        currentStateLabel.setText("Current State:");
        currentStateLabel.addClassName("publication-current-state-label");

        Div badges = uiComponents.create(Div.class);
        badges.addClassName("publication-status-badges");
        badges.add(
                createBadge(decision.effectivePublished()
                        ? messageBundle.getMessage("publicationBadgePublished")
                        : messageBundle.getMessage("publicationBadgeUnpublished"),
                        decision.effectivePublished() ? "is-published" : "is-unpublished"),
                createBadge(messageBundle.getMessage("publicationModeBadgePrefix") + " "
                        + getPublicationModeLabel(decision.publicationMode()),
                        "is-mode")
        );

        Paragraph webhookHint = uiComponents.create(Paragraph.class);
        webhookHint.addClassName("publication-webhook-hint");
        webhookHint.setText(decision.webhookHint());

        Button applyButton = uiComponents.create(Button.class);
        applyButton.setId("applyPublicationModeButton");
        applyButton.setText(messageBundle.getMessage("publicationApplyButton"));
        applyButton.addClassName("publication-apply-button");
        applyButton.addClickListener(click -> applyPublicationMode());

        publicationControlContainer.add(modeLabel, publicationModeGroup, applyButton, currentStateLabel, badges, webhookHint);
    }

    private void applyPublicationMode() {
        Event event = getEditedEntity();
        PublicationMode selectedMode = publicationModeGroup != null ? publicationModeGroup.getValue() : null;
        if (event == null || selectedMode == null) {
            return;
        }

        eventPublicationService.setPublicationMode(event, selectedMode, "admin_mode_" + selectedMode.getId(), nowUtc());
        renderPublicationControls(event);
        notifications.create(messageBundle.getMessage("publicationModeSavedNotification"))
                .withThemeVariant(NotificationVariant.LUMO_SUCCESS)
                .show();
    }

    private void sortTicketTiers() {
        List<EventTicketTier> sortedTiers = new ArrayList<>(ticketTiersDc.getItems());
        sortedTiers.sort(TICKET_TIER_ORDER);
        ticketTiersDc.setDisconnectedItems(sortedTiers);
    }

    private String buildDescriptionMarkup(final Event event) {
        String description = event != null ? event.getDescription() : null;
        if (description == null || description.isBlank()) {
            return """
                    <div class="event-description-preview__title">Description</div>
                    <div class="event-description-preview__empty">No description</div>
                    """;
        }

        return """
                <div class="event-description-preview__title">Description</div>
                <div class="event-description-preview__content">%s</div>
                """.formatted(sanitizeDescriptionHtml(description));
    }

    private String sanitizeDescriptionHtml(final String description) {
        String sanitizedHtml = Jsoup.clean(description, "", DESCRIPTION_HTML_SAFELIST, new Document.OutputSettings().prettyPrint(false));
        Document document = Jsoup.parseBodyFragment(sanitizedHtml);
        document.outputSettings().prettyPrint(false);
        document.select("a[href]").forEach(link -> {
            link.attr("target", "_blank");
            link.attr("rel", "noopener noreferrer");
        });
        return document.body().html();
    }

    private void renderVenueDetails(final Event event) {
        venueContainer.removeAll();
        venueContainer.addClassNames("event-detail-card", "event-detail-card--venue");
        venueContainer.add(createSectionTitle("Venue"));

        JsonNode venueNode = readJsonObject(event != null ? event.getVenueJson() : null);
        if (venueNode == null || venueNode.isEmpty()) {
            venueContainer.add(createEmptyState("No venue details"));
            return;
        }

        String address = textValue(venueNode, "full_address");
        String suburb = textValue(venueNode, "suburb");
        String phone = textValue(venueNode, "phone");
        String email = textValue(venueNode, "email");
        String countryCode = textValue(venueNode, "country_code");
        String timezone = textValue(venueNode, "timezone");

        addMetaRow(venueContainer, "Address", address);
        addMetaRow(venueContainer, "Suburb", suburb);
        addMetaRow(venueContainer, "Phone", phone);
        addMetaRow(venueContainer, "Email", email);
        addMetaRow(venueContainer, "Country", countryCode);
        addMetaRow(venueContainer, "Venue timezone", timezone);
    }

    private void renderPhotos(final Event event) {
        photosContainer.removeAll();
        photosContainer.addClassNames("event-detail-card", "event-detail-card--photos");
        photosContainer.add(createSectionTitle("Photos"));

        JsonNode photosNode = readJsonArray(event != null ? event.getPhotosJson() : null);
        if (photosNode == null || photosNode.isEmpty()) {
            photosContainer.add(createEmptyState("No photos"));
            return;
        }

        Div gallery = uiComponents.create(Div.class);
        gallery.addClassName("event-photo-gallery");

        int index = 0;
        for (JsonNode photoNode : photosNode) {
            String photoUrl = photoNode.asText(null);
            if (photoUrl == null || photoUrl.isBlank()) {
                continue;
            }

            gallery.add(createPhotoThumbnail(photoUrl, index++));
        }

        if (gallery.getChildren().findAny().isEmpty()) {
            photosContainer.add(createEmptyState("No photos"));
            return;
        }

        photosContainer.add(gallery);
    }

    private H4 createSectionTitle(final String title) {
        H4 heading = uiComponents.create(H4.class);
        heading.setText(title);
        heading.addClassName("event-detail-card__title");
        return heading;
    }

    private Paragraph createEmptyState(final String text) {
        Paragraph empty = uiComponents.create(Paragraph.class);
        empty.setText(text);
        empty.addClassName("event-detail-card__empty");
        return empty;
    }

    private void addMetaRow(final Div container, final String label, final String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        Div row = uiComponents.create(Div.class);
        row.addClassName("event-detail-card__row");

        Span labelSpan = uiComponents.create(Span.class);
        labelSpan.setText(label);
        labelSpan.addClassName("event-detail-card__label");

        Span valueSpan = uiComponents.create(Span.class);
        valueSpan.setText(value);
        valueSpan.addClassName("event-detail-card__value");

        row.add(labelSpan, valueSpan);
        container.add(row);
    }

    private Span createBadge(final String text, final String variantClass) {
        Span badge = uiComponents.create(Span.class);
        badge.setText(text);
        badge.addClassNames("publication-status-badge", variantClass);
        return badge;
    }

    private String getPublicationModeLabel(final PublicationMode mode) {
        return messages.getMessage("app.rubeton.toniq.entity/PublicationMode." + mode.name());
    }

    private Image createPhotoThumbnail(final String photoUrl, final int index) {
        Image image = uiComponents.create(Image.class);
        image.setSrc(photoUrl);
        image.setAlt("Event photo " + (index + 1));
        image.addClassName("event-photo-gallery__thumb");
        image.getStyle().set("cursor", "pointer");
        image.addClickListener(click -> openPhotoDialog(photoUrl, image.getAlt().orElse("Event photo")));
        return image;
    }

    private void openPhotoDialog(final String photoUrl, final String altText) {
        Dialog dialog = uiComponents.create(Dialog.class);
        dialog.setModal(true);
        dialog.setDraggable(false);
        dialog.setResizable(true);
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(true);
        dialog.setWidth("min(96vw, 1100px)");
        dialog.setMaxWidth("1100px");

        Div content = uiComponents.create(Div.class);
        content.addClassName("event-photo-dialog");

        Image fullImage = uiComponents.create(Image.class);
        fullImage.setSrc(photoUrl);
        fullImage.setAlt(altText);
        fullImage.addClassName("event-photo-dialog__image");

        Button closeButton = uiComponents.create(Button.class);
        closeButton.setText("Close");
        closeButton.addClassName("event-photo-dialog__close");
        closeButton.addClickListener(click -> dialog.close());

        content.add(fullImage, closeButton);
        dialog.add(content);
        dialog.open();
    }

    private JsonNode readJsonObject(final String rawJson) {
        JsonNode node = readJson(rawJson);
        return node != null && node.isObject() ? node : null;
    }

    private JsonNode readJsonArray(final String rawJson) {
        JsonNode node = readJson(rawJson);
        return node != null && node.isArray() ? node : null;
    }

    private JsonNode readJson(final String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readTree(rawJson);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String textValue(final JsonNode node, final String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }

        String text = fieldNode.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
