package com.wornux.ui.conversation;

import com.wornux.ui.css.UiCss;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.signals.Signal;
import com.wornux.services.chat.ModelAvailabilityStatus;

public final class ConversationComposer extends Composite<Div> {

    private final TextArea field;
    private final Span modelStatus;
    private final Button sendButton;

    public ConversationComposer(ConversationState state, int promptLimit, Runnable submitHandler) {
        field = new TextArea();
        field.setWidthFull();
        field.setPlaceholder("Escribe tu mensaje aquí...");
        field.setAriaLabel("Escribe tu mensaje aquí");
        field.setMaxLength(promptLimit);
        updateHelperText(0, promptLimit);
        UiCss.CONVERSATION_COMPOSER_INPUT.addTo(field);
        field.bindValue(state.composerText(), state.composerText()::set);
        field.bindEnabled(state.composerEnabled());
        field.setValueChangeMode(ValueChangeMode.EAGER);
        field.addValueChangeListener(event -> updateHelperText(event.getValue().length(), promptLimit));

        modelStatus = new Span();
        UiCss.CONVERSATION_COMPOSER_MODEL_STATUS.addTo(modelStatus);
        modelStatus.getElement().setAttribute("aria-live", "polite");
        Signal.effect(modelStatus, () -> updateModelStatus(state.modelAvailabilityStatus().get()));

        sendButton = new Button(new Icon(VaadinIcon.ARROW_UP));
        UiCss.CONVERSATION_COMPOSER_SEND_BUTTON.addTo(sendButton);
        sendButton.setAriaLabel("Enviar mensaje");
        sendButton.bindEnabled(state.sendEnabled());
        sendButton.addClickListener(_ -> submitHandler.run());

        var content = getContent();
        UiCss.CONVERSATION_COMPOSER_FIELD_WRAP.addTo(content);
        content.add(field, modelStatus, sendButton);
        content.addAttachListener(_ -> installEnterSubmitHandler());
    }

    private void updateHelperText(int currentLength, int promptLimit) {
        field.setHelperText("%d/%d caracteres".formatted(currentLength, promptLimit));
    }

    private void updateModelStatus(ModelAvailabilityStatus status) {
        var resolvedStatus = status == null ? ModelAvailabilityStatus.CHECKING : status;
        modelStatus.setText(labelFor(resolvedStatus));
        modelStatus.getElement().getClassList().set("is-checking", resolvedStatus == ModelAvailabilityStatus.CHECKING);
        modelStatus.getElement().getClassList().set("is-connected", resolvedStatus == ModelAvailabilityStatus.CONNECTED);
        modelStatus.getElement().getClassList().set("is-offline", resolvedStatus == ModelAvailabilityStatus.OFFLINE);
    }

    private String labelFor(ModelAvailabilityStatus status) {
        return switch (status) {
            case CONNECTED -> "Connected";
            case OFFLINE -> "Offline";
            case CHECKING -> "Checking";
        };
    }

    private void installEnterSubmitHandler() {
        getContent().getElement().executeJs("""
                                           if (this.__enterSubmitInstalled) {
                                             return;
                                           }
                                           this.__enterSubmitInstalled = true;

                                           const field = this.querySelector('vaadin-text-area');
                                           const button = this.querySelector('vaadin-button');
                                           field?.addEventListener('keydown', (event) => {
                                             if (event.key !== 'Enter' || event.shiftKey) {
                                               return;
                                             }
                                             event.preventDefault();
                                             event.stopImmediatePropagation();
                                             if (!button?.hasAttribute('disabled')) {
                                               button?.click();
                                             }
                                           }, true);
                                           """);
    }
}
