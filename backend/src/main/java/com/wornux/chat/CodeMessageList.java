package com.wornux.chat;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.internal.JacksonUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Tag("code-message-list")
@JsModule("./code-message-list.ts")
@NpmPackage(value = "@uiw/react-codemirror", version = "4.25.4")
@NpmPackage(value = "@codemirror/theme-one-dark", version = "6.1.3")
@NpmPackage(value = "@codemirror/lang-json", version = "6.0.2")
@NpmPackage(value = "@codemirror/lang-xml", version = "6.1.0")
@NpmPackage(value = "@codemirror/lang-javascript", version = "6.2.4")
@NpmPackage(value = "@codemirror/lang-python", version = "6.1.7")
@NpmPackage(value = "@codemirror/lang-java", version = "6.0.1")
@NpmPackage(value = "@codemirror/lang-cpp", version = "6.0.2")
public final class CodeMessageList extends Component implements HasStyle, HasSize {

    private List<CodeMessageListItem> items = new ArrayList<>();
    private boolean pendingUpdate;
    private boolean pendingTextUpdate;
    private Integer pendingAddItemsIndex;

    public void setItems(Collection<CodeMessageListItem> items) {
        Objects.requireNonNull(items, "Can't set null item collection to CodeMessageList.");
        items.forEach(item -> Objects.requireNonNull(item, "Can't include null items in CodeMessageList."));

        this.items.forEach(item -> item.setHost(null));
        this.items = new ArrayList<>(items);
        this.items.forEach(item -> item.setHost(this));
        scheduleItemsUpdate();
    }

    public void addItem(CodeMessageListItem item) {
        Objects.requireNonNull(item, "Can't add null item to CodeMessageList.");

        item.setHost(this);
        items.add(item);
        scheduleAddItemsUpdate();
    }

    public List<CodeMessageListItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void setMarkdown(boolean markdown) {
        getElement().setProperty("markdown", markdown);
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
        } else {
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
            } else {
                getElement().executeJs("this.setItemText($0, $1)", item.getText(), items.indexOf(item));
            }
            item.clientText = item.getText();
        });
    }
}
