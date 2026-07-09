package com.wornux.ui.training_activity;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;

@Tag("training-activity-report-cards")
@JsModule("./training-activity/report/cards.ts")
public class TrainingActivityReportCards extends Component {

    public void setItemsJson(String itemsJson) {
        getElement().setProperty("itemsJson", itemsJson == null ? "[]" : itemsJson);
    }
}
