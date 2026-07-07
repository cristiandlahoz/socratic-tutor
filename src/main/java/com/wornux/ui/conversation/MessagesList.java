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
import com.wornux.services.chat.ChatSessionActivity;

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

        var nextItems = new ArrayList<>(items);

        if (canPatchByAppendingOrGrowingText(this.items, nextItems)) {
            patchItems(nextItems);
            return;
        }

        replaceItems(nextItems);
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

    public void setActivity(ChatSessionActivity activity) {
        var resolvedActivity = activity == null ? ChatSessionActivity.IDLE : activity;
        getElement().setProperty("activity", resolvedActivity.name().toLowerCase(java.util.Locale.ROOT));
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

        getElement().getNode().runWhenAttached(ui -> ui.beforeClientResponse(this, _ -> updateClient()));
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

    private void replaceItems(List<MessageItem> nextItems) {
        this.items.forEach(item -> item.setHost(null));

        this.items = nextItems;
        this.items.forEach(item -> item.setHost(this));
    }

    private void patchItems(List<MessageItem> nextItems) {
        var previousItems = this.items;
        var previousSize = previousItems.size();
        var sharedSize = Math.min(previousSize, nextItems.size());
        var textUpdateRequired = false;

        for (var index = 0; index < sharedSize; index += 1) {
            var previousItem = previousItems.get(index);
            var nextItem = nextItems.get(index);

            previousItem.setHost(null);
            nextItem.clientText = previousItem.clientText;
            nextItem.setHost(this);

            textUpdateRequired = textUpdateRequired || !Objects.equals(nextItem.getText(), nextItem.clientText);
        }

        for (var index = sharedSize; index < nextItems.size(); index += 1) {
            nextItems.get(index).setHost(this);
        }

        this.items = nextItems;

        if (!textUpdateRequired && nextItems.size() == previousSize) {
            return;
        }

        scheduleUpdate();
        pendingTextUpdate = textUpdateRequired;

        if (nextItems.size() > previousSize && pendingAddItemsIndex == null) {
            pendingAddItemsIndex = previousSize;
        }
    }

    private boolean canPatchByAppendingOrGrowingText(List<MessageItem> previousItems, List<MessageItem> nextItems) {
        if (previousItems.isEmpty()) {
            return nextItems.isEmpty();
        }

        if (nextItems.size() < previousItems.size()) {
            return false;
        }

        for (var index = 0; index < previousItems.size(); index += 1) {
            var previousItem = previousItems.get(index);
            var nextItem = nextItems.get(index);

            if (!sameClientIdentity(previousItem, nextItem)) {
                return false;
            }

            if (index < previousItems.size() - 1 && !Objects.equals(previousItem.getText(), nextItem.getText())) {
                return false;
            }

            if (index == previousItems.size() - 1 && !nextItem.getText().startsWith(previousItem.getText())) {
                return false;
            }
        }

        return true;
    }

    private boolean sameClientIdentity(MessageItem left, MessageItem right) {
        return Objects.equals(left.getTime(), right.getTime())
                && Objects.equals(left.getUserName(), right.getUserName())
                && Objects.equals(left.getVariant(), right.getVariant())
                && left.isLoading() == right.isLoading();
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
        for (var index = 0; index < items.size(); index += 1) {
            var item = items.get(index);
            var textChanged = !Objects.equals(item.getText(), item.clientText);

            if (!textChanged) {
                continue;
            }

            if (item.getText() != null && item.clientText != null && item.getText().startsWith(item.clientText)) {
                var diff = item.getText().substring(item.clientText.length());
                getElement().executeJs("this.appendItemText($0, $1)", diff, index);
            }
            else {
                getElement().executeJs("this.setItemText($0, $1)", item.getText(), index);
            }

            item.clientText = item.getText();
        }
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