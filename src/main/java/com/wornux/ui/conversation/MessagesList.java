package com.wornux.ui.conversation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

@Tag("messages-list")
@JsModule("./conversation/messages-list.ts")
public final class MessagesList extends Component implements HasSize {

    private transient List<MessageItem> items = new ArrayList<>();
    private boolean pendingUpdate;
    private boolean pendingTextUpdate;
    private Integer pendingAddItemsIndex;

    public MessagesList() {
        addAttachListener(_ -> scheduleItemsUpdate());
    }

    public void setItems(Collection<MessageItem> items) {
        Objects.requireNonNull(items, "Can't set null item collection to MessagesList.");
        items.forEach(item -> Objects.requireNonNull(item, "Can't include null items in MessagesList."));

        this.items.forEach(item -> item.setHost(null));
        this.items = new ArrayList<>(items);
        this.items.forEach(item -> item.setHost(this));
        scheduleItemsUpdate();
    }

    public void addItem(MessageItem item) {
        Objects.requireNonNull(item, "Can't add null item to MessagesList.");

        item.setHost(this);
        items.add(item);
        scheduleAddItemsUpdate();
    }

    public List<MessageItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void setThinkingSpinner(String thinkingSpinner) {
        if (thinkingSpinner == null || thinkingSpinner.isBlank()) {
            getElement().setProperty("thinkingSpinner", "braille");
            return;
        }
        getElement().setProperty("thinkingSpinner", thinkingSpinner.trim());
    }

    public Registration addDebugCodeRequestListener(ComponentEventListener<DebugCodeRequestEvent> listener) {
        return addListener(DebugCodeRequestEvent.class, listener);
    }

    void scheduleItemsTextUpdate() {
        scheduleUpdate();
        pendingTextUpdate = true;
    }

    void scheduleItemsUpdate() {
        scheduleUpdate();
        pendingUpdate = true;
    }

    private void scheduleAddItemsUpdate() {
        scheduleUpdate();
        if (pendingAddItemsIndex == null) {
            pendingAddItemsIndex = items.size() - 1;
        }
    }

    private void scheduleUpdate() {
        if (pendingUpdate || pendingTextUpdate || pendingAddItemsIndex != null) {
            return;
        }

        getElement().getNode().runWhenAttached(ui -> ui.beforeClientResponse(this, ctx -> updateClient()));
    }

    private void updateClient() {
        if (pendingUpdate) {
            handleFullUpdate();
        }
        else {
            handleAddItemsUpdate();
            handleTextUpdates();
        }

        pendingTextUpdate = false;
        pendingUpdate = false;
        pendingAddItemsIndex = null;
    }

    private void handleFullUpdate() {
        items.forEach(item -> item.clientText = item.getText());
        var itemsJson = JacksonUtils.listToJson(items);
        getElement().executeJs("this.setItems($0)", itemsJson);
    }

    private void handleAddItemsUpdate() {
        if (pendingAddItemsIndex == null) {
            return;
        }

        var newItems = items.subList(pendingAddItemsIndex, items.size());
        newItems.forEach(item -> item.clientText = item.getText());
        var itemsJson = JacksonUtils.listToJson(newItems);
        getElement().executeJs("this.addItems($0)", itemsJson);
    }

    private void handleTextUpdates() {
        items.forEach(item -> {
            var textChanged = !Objects.equals(item.getText(), item.clientText);
            if (!textChanged) {
                return;
            }

            if (item.getText() != null && item.clientText != null && item.getText().startsWith(item.clientText)) {
                var diff = item.getText().substring(item.clientText.length());
                getElement().executeJs("this.appendItemText($0, $1)", diff, items.indexOf(item));
            }
            else {
                getElement().executeJs("this.setItemText($0, $1)", item.getText(), items.indexOf(item));
            }
            item.clientText = item.getText();
        });
    }

    @DomEvent("debug-code-requested")
    public static final class DebugCodeRequestEvent extends ComponentEvent<MessagesList> {

        private final String code;
        private final String lang;

        public DebugCodeRequestEvent(
                MessagesList source,
                boolean fromClient,
                @EventData("event.detail.code") String code,
                @EventData("event.detail.lang") String lang) {
            super(source, fromClient);
            this.code = code == null ? "" : code;
            this.lang = lang == null ? "" : lang;
        }

        public String getCode() {
            return code;
        }

        public String getLang() {
            return lang;
        }
    }
}
