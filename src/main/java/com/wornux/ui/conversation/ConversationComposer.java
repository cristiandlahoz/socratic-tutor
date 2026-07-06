package com.wornux.ui.conversation;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.signals.Signal;
import com.wornux.services.chat.ModelAvailabilityStatus;
import com.wornux.ui.css.UiCss;

@Tag("conversation-composer")
@JsModule("./conversation/conversation-composer.ts")
public final class ConversationComposer extends Component implements HasSize {

    public ConversationComposer(ConversationState state, int promptLimit, PromptSubmitHandler submitHandler) {
        UiCss.CONVERSATION_COMPOSER_FIELD_WRAP.addTo(this);
        setPromptLimit(promptLimit);

        Signal.effect(this, () -> setValue(state.composerText().get()));
        Signal.effect(this, () -> setComposerEnabled(Boolean.TRUE.equals(state.composerEnabled().get())));
        Signal.effect(this, () -> setSendAvailable(Boolean.TRUE.equals(state.composerSubmitAllowed().get())));
        Signal.effect(this, () -> setModelStatus(state.modelAvailabilityStatus().get()));

        addSubmitPromptListener(event -> {
            state.composerText().set(event.getPrompt());
            submitHandler.submit();
        });
    }

    public void setValue(String value) {
        getElement().setProperty("value", value == null ? "" : value);
    }

    public void setPromptLimit(int promptLimit) {
        getElement().setProperty("promptLimit", promptLimit);
    }

    public void setComposerEnabled(boolean composerEnabled) {
        getElement().setProperty("composerEnabled", composerEnabled);
    }

    public void setSendAvailable(boolean sendAvailable) {
        getElement().setProperty("sendAvailable", sendAvailable);
    }

    public void setModelStatus(ModelAvailabilityStatus status) {
        var resolvedStatus = status == null ? ModelAvailabilityStatus.CHECKING : status;
        getElement().setProperty("modelStatus", resolvedStatus.name().toLowerCase());
    }

    public Registration addSubmitPromptListener(ComponentEventListener<SubmitPromptEvent> listener) {
        return addListener(SubmitPromptEvent.class, listener);
    }

    @FunctionalInterface
    public interface PromptSubmitHandler {
        void submit();
    }

    @DomEvent("submit-prompt")
    public static final class SubmitPromptEvent extends ComponentEvent<ConversationComposer> {

        private final String prompt;

        public SubmitPromptEvent(
                ConversationComposer source,
                boolean fromClient,
                @EventData("event.detail.prompt") String prompt) {
            super(source, fromClient);
            this.prompt = prompt == null ? "" : prompt;
        }

        public String getPrompt() {
            return prompt;
        }
    }
}
