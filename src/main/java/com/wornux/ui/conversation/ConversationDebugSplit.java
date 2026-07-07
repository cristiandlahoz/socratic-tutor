package com.wornux.ui.conversation;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;

@Tag("conversation-debug-split")
@JsModule("./conversation/conversation-debug-split.ts")
public final class ConversationDebugSplit extends Component implements HasSize {

    public ConversationDebugSplit(Component primary, Component secondary) {
        setSizeFull();
        primary.getElement().setAttribute("slot", "primary");
        secondary.getElement().setAttribute("slot", "secondary");
        getElement().appendChild(primary.getElement(), secondary.getElement());
        setDebuggerVisible(false);
    }

    public void setDebuggerVisible(boolean visible) {
        getElement().setProperty("debuggerVisible", visible);
    }
}
