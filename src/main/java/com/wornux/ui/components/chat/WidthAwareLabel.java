package com.wornux.ui.components.chat;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.html.Span;
import com.wornux.ui.css.UiCss;

/**
 * A reusable label that truncates its text according to the rendered width and replaces the last three visible
 * characters with an ellipsis when needed.
 */
public class WidthAwareLabel extends Composite<Span> {

    private String fullText = "";

    public WidthAwareLabel() {
        UiCss.WIDTH_AWARE_LABEL.addTo(getContent());
    }

    public WidthAwareLabel(String text) {
        this();
        setText(text);
    }

    public void setText(String text) {
        fullText = text == null ? "" : text;
        var element = getContent().getElement();
        element.setAttribute("data-full-text", fullText);
        element.setAttribute("title", fullText);
        element.setAttribute("aria-label", fullText);
        getContent().setText(fullText);
        refreshDisplay();
    }

    public String getText() {
        return fullText;
    }

    public void setSafetyPixels(int safetyPixels) {
        int safetyPixels1 = Math.max(0, safetyPixels);
        getContent().getElement().setProperty("__widthAwareSafety", safetyPixels1);
        refreshDisplay();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        installObserver();
        refreshDisplay();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        getContent().getElement().executeJs("this.__widthAwareCleanup?.();");
        super.onDetach(detachEvent);
    }

    private void installObserver() {
        getContent().getElement().executeJs("""
                                            const el = this;
                                            if (el.__widthAwareCleanup) {
                                              el.__widthAwareCleanup();
                                            }

                                            const canvas = document.createElement('canvas');
                                            const context = canvas.getContext('2d');

                                            const applyText = () => {
                                              const fullText = el.dataset.fullText ?? '';
                                              const reserve = Number(el.__widthAwareSafety ?? 12);
                                              el.title = fullText;
                                              el.setAttribute('aria-label', fullText);

                                              if (!fullText) {
                                                el.textContent = '';
                                                return;
                                              }

                                              const availableWidth = Math.max(0, el.clientWidth - reserve);
                                              if (availableWidth === 0) {
                                                el.textContent = fullText;
                                                return;
                                              }

                                              const styles = getComputedStyle(el);
                                              context.font = [
                                                styles.fontStyle,
                                                styles.fontVariant,
                                                styles.fontWeight,
                                                styles.fontSize,
                                                styles.fontFamily
                                              ].join(' ');

                                              const measure = (value) => context.measureText(value).width;
                                              if (measure(fullText) <= availableWidth) {
                                                el.textContent = fullText;
                                                return;
                                              }

                                              let low = 0;
                                              let high = fullText.length;
                                              let best = '...';

                                              while (low <= high) {
                                                const mid = Math.floor((low + high) / 2);
                                                const nextValue = mid <= 3 ? '...' : fullText.slice(0, mid - 3) + '...';
                                                if (measure(nextValue) <= availableWidth) {
                                                  best = nextValue;
                                                  low = mid + 1;
                                                } else {
                                                  high = mid - 1;
                                                }
                                              }

                                              el.textContent = best;
                                            };

                                            const resizeObserver = new ResizeObserver(() => applyText());
                                            resizeObserver.observe(el);
                                            el.__widthAwareApply = applyText;
                                            el.__widthAwareCleanup = () => {
                                              resizeObserver.disconnect();
                                              delete el.__widthAwareApply;
                                              delete el.__widthAwareCleanup;
                                            };
                                            applyText();
                                            """);
    }

    private void refreshDisplay() {
        if (getUI().isPresent()) {
            getContent().getElement().executeJs("this.__widthAwareApply?.();");
        }
    }
}
