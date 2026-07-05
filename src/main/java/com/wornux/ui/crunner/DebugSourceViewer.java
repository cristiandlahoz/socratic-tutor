package com.wornux.ui.crunner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.internal.JacksonUtils;
import com.vaadin.flow.shared.Registration;
import com.wornux.services.crunner.CDiagnostic;

@Tag("c-debug-source-viewer")
@JsModule("./crunner/c-debug-source-viewer.tsx")
public final class DebugSourceViewer extends Component implements HasSize {

    public DebugSourceViewer() {
        setLang("c");
    }

    public void setValue(String value) {
        getElement().setProperty("value", value == null ? "" : value);
    }

    public void setLang(String lang) {
        getElement().setProperty("lang", lang == null || lang.isBlank() ? "c" : lang);
    }

    public void setDiagnostics(Collection<CDiagnostic> diagnostics) {
        var safeDiagnostics = diagnostics == null ? List.<CDiagnostic>of() : new ArrayList<>(diagnostics);
        getElement().setPropertyJson("diagnostics", JacksonUtils.listToJson(safeDiagnostics));
    }

    public void setActiveLine(Integer activeLine) {
        getElement().setProperty("activeLine", activeLine == null ? 0 : activeLine);
    }

    public void setEditable(boolean editable) {
        getElement().setProperty("editable", editable);
    }

    public Registration addValueChangeListener(ComponentEventListener<ValueChangedEvent> listener) {
        return addListener(ValueChangedEvent.class, listener);
    }

    @DomEvent("value-changed")
    public static final class ValueChangedEvent extends ComponentEvent<DebugSourceViewer> {

        private final String value;

        public ValueChangedEvent(
                DebugSourceViewer source,
                boolean fromClient,
                @EventData("event.detail.value") String value) {
            super(source, fromClient);
            this.value = value == null ? "" : value;
        }

        public String getValue() {
            return value;
        }
    }
}
