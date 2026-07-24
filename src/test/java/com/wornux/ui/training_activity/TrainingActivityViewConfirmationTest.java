package com.wornux.ui.training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import com.wornux.services.training_activity.SafeBrowserAssignmentStateBus;
import com.wornux.services.training_activity.SafeBrowserModeService;
import com.wornux.services.training_activity.TrainingActivityReportProjectionService;
import com.wornux.services.training_activity.TrainingActivityService;
import com.wornux.services.workspace.WorkspaceRoutingService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TrainingActivityViewConfirmationTest extends BrowserlessTest {

    @Test
    void launchSelectedPublishesDirectlyWithoutOpeningAConfirmationDialog() {
        var ui = UI.getCurrent();
            var service = mock(TrainingActivityService.class);
            when(service.listAll()).thenReturn(List.of());
            var view = new TrainingActivityView(
                    service,
                    mock(SafeBrowserModeService.class),
                    new SafeBrowserAssignmentStateBus(),
                    mock(TrainingActivityReportProjectionService.class),
                    mock(WorkspaceRoutingService.class),
                    mock(AuthenticatedUserContextUtils.class));
            ui.add(view);
            var activity = new TrainingActivity();
            ReflectionTestUtils.setField(activity, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(activity, "status", TrainingActivityLifecycleStatus.DRAFT);
            when(service.launch(activity.getId(), activity.getVersion())).thenReturn(1);

            @SuppressWarnings("unchecked")
            var grid = (com.vaadin.flow.component.grid.Grid<TrainingActivity>) ReflectionTestUtils.getField(view, "grid");
            grid.asSingleSelect().setValue(activity);

            ReflectionTestUtils.invokeMethod(view, "onLaunchSelected");

            verify(service).launch(activity.getId(), activity.getVersion());
            assertThat(view.getContent().getChildren()).noneMatch(ConfirmDialog.class::isInstance);
    }
}
