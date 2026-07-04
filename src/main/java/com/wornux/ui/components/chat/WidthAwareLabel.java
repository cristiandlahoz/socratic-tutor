package com.wornux.ui.components.chat;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.wornux.ui.css.UiCss;

/**
 * A reusable label that truncates its text according to the rendered width and replaces the last three visible
 * characters with an ellipsis when needed.
 */
@Tag("width-aware-label")
@JsModule("./shell/width-aware-label.ts")
public class WidthAwareLabel extends Component {

    private String fullText = "";

    public WidthAwareLabel() {
        UiCss.WIDTH_AWARE_LABEL.addTo(this);
    }

    public WidthAwareLabel(String text) {
        this();
        setText(text);
    }

    public void setText(String text) {
        fullText = text == null ? "" : text;
        getElement().setProperty("fullText", fullText);
    }

    public String getText() {
        return fullText;
    }

    public void setSafetyPixels(int safetyPixels) {
        getElement().setProperty("safetyPixels", Math.max(0, safetyPixels));
    }
}
