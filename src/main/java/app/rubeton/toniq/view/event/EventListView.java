package app.rubeton.toniq.view.event;

import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.SyncLogStatus;
import app.rubeton.toniq.service.megatix.ManualSyncStatusService;
import app.rubeton.toniq.service.megatix.MegatixSyncCoordinator;
import app.rubeton.toniq.service.megatix.model.ManualSyncHandle;
import app.rubeton.toniq.service.megatix.model.ManualSyncStatus;
import com.vaadin.flow.component.ClickEvent;
import app.rubeton.toniq.view.main.MainView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.facet.Timer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.MessageBundle;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.MessageFormat;
import java.util.UUID;

@Route(value = "events", layout = MainView.class)
@ViewController(id = "Event.list")
@ViewDescriptor(path = "event-list-view.xml")
@LookupComponent("eventsDataGrid")
@DialogMode(width = "72em")
public class EventListView extends StandardListView<Event> {

    private static final int POLL_INTERVAL_MS = 1_000;
    private static final int MAX_POLL_ATTEMPTS = 60;

    @ViewComponent
    private DataGrid<Event> eventsDataGrid;
    @ViewComponent
    private MessageBundle messageBundle;
    @ViewComponent
    private CollectionLoader<Event> eventsDl;
    @ViewComponent
    private Timer manualSyncTimer;
    @Autowired
    private Notifications notifications;
    @Autowired
    private MegatixSyncCoordinator megatixSyncCoordinator;
    @Autowired
    private ManualSyncStatusService manualSyncStatusService;

    private UUID activeSyncLogId;
    private int pollAttempts;

    @Subscribe("importByIdButton")
    public void onImportByIdButtonClick(final ClickEvent<Button> event) {
        openImportDialog();
    }

    @Subscribe("resyncButton")
    public void onResyncButtonClick(final ClickEvent<Button> event) {
        Event selected = eventsDataGrid.getSingleSelectedItem();
        if (selected == null) {
            notifications.create(messageBundle.getMessage("selectEventNotification"))
                    .withThemeVariant(NotificationVariant.LUMO_WARNING)
                    .show();
            return;
        }

        ManualSyncHandle handle = megatixSyncCoordinator.submitManualResync(selected.getMegatixEventId());
        startManualSyncPolling(handle.getSyncLogId());
        notifications.create(formatMessage("manualResyncQueuedNotification", selected.getMegatixEventId()))
                .withThemeVariant(NotificationVariant.LUMO_SUCCESS)
                .show();
    }

    private void openImportDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(messageBundle.getMessage("importDialogTitle"));

        TextField eventIdField = new TextField(messageBundle.getMessage("importDialogEventId"));
        eventIdField.setWidthFull();
        eventIdField.setRequired(true);

        Button cancelButton = new Button(messageBundle.getMessage("importDialogCancel"), click -> dialog.close());
        Button importButton = new Button(messageBundle.getMessage("importDialogConfirm"), click -> {
            String eventId = eventIdField.getValue();
            if (eventId == null || eventId.isBlank()) {
                notifications.create(messageBundle.getMessage("eventIdRequiredNotification"))
                        .withThemeVariant(NotificationVariant.LUMO_ERROR)
                        .show();
                return;
            }

            ManualSyncHandle handle = megatixSyncCoordinator.submitManualImport(eventId.trim());
            startManualSyncPolling(handle.getSyncLogId());
            notifications.create(formatMessage("manualImportQueuedNotification", eventId.trim()))
                    .withThemeVariant(NotificationVariant.LUMO_SUCCESS)
                    .show();
            dialog.close();
        });

        HorizontalLayout actions = new HorizontalLayout(cancelButton, importButton);
        VerticalLayout content = new VerticalLayout(eventIdField, actions);
        content.setPadding(false);
        content.setSpacing(true);
        dialog.add(content);
        dialog.open();
    }

    private void startManualSyncPolling(final UUID syncLogId) {
        activeSyncLogId = syncLogId;
        pollAttempts = 0;
        manualSyncTimer.start();
    }

    @Subscribe("manualSyncTimer")
    public void onManualSyncTimerAction(final Timer.TimerActionEvent event) {
        if (activeSyncLogId == null) {
            stopManualSyncPolling();
            return;
        }

        pollAttempts++;
        ManualSyncStatus status = manualSyncStatusService.getStatus(activeSyncLogId);
        if (!status.isTerminal()) {
            if (pollAttempts >= MAX_POLL_ATTEMPTS) {
                notifications.create(messageBundle.getMessage("manualSyncPollingTimeoutNotification"))
                        .withThemeVariant(NotificationVariant.LUMO_WARNING)
                        .show();
                stopManualSyncPolling();
            }
            return;
        }

        if (status.getStatus() == SyncLogStatus.SUCCESS) {
            eventsDl.load();
            notifications.create(formatMessage("manualSyncCompletedNotification", status.getMegatixEventId()))
                    .withThemeVariant(NotificationVariant.LUMO_SUCCESS)
                    .show();
        } else if (status.getStatus() == SyncLogStatus.FAILURE) {
            notifications.create(buildFailureMessage(status))
                    .withThemeVariant(NotificationVariant.LUMO_ERROR)
                    .show();
        } else {
            notifications.create(formatMessage("manualSyncIgnoredNotification", status.getMegatixEventId()))
                    .withThemeVariant(NotificationVariant.LUMO_WARNING)
                    .show();
        }
        stopManualSyncPolling();
    }

    private String buildFailureMessage(final ManualSyncStatus status) {
        if (status.getErrorMessage() != null && !status.getErrorMessage().isBlank()) {
            return formatMessage("manualSyncFailedWithReasonNotification",
                    status.getMegatixEventId(), status.getErrorMessage());
        }
        return formatMessage("manualSyncFailedNotification", status.getMegatixEventId());
    }

    private void stopManualSyncPolling() {
        manualSyncTimer.stop();
        activeSyncLogId = null;
        pollAttempts = 0;
    }

    private String formatMessage(final String key, final Object... params) {
        return MessageFormat.format(messageBundle.getMessage(key), params);
    }
}
