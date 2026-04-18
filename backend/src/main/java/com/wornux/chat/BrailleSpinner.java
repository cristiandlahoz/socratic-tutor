package com.wornux.chat;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;

@Tag("braille-spinner")
@JsModule("./braille-spinner.ts")
public class BrailleSpinner extends Component {

    public void setSpinner(String spinner) {
        getElement().setProperty("spinner", spinner == null || spinner.isBlank() ? "braille" : spinner.trim());
    }
}
