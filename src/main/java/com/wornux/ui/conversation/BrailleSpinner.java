package com.wornux.ui.conversation;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;

@Tag("braille-spinner")
@JsModule("./conversation/braille-spinner.ts")
public final class BrailleSpinner extends Component {

    public BrailleSpinner(String spinner) {
        setSpinner(spinner);
    }

    public void setSpinner(String spinner) {
        getElement().setProperty("spinner", spinner == null || spinner.isBlank() ? "braille" : spinner.trim());
    }
}
