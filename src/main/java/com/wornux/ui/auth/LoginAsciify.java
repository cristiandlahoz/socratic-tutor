package com.wornux.ui.auth;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;

@Tag("login-asciify")
@JsModule("./auth/login-asciify.ts")
final class LoginAsciify extends Component implements HasComponents, HasSize {

    LoginAsciify(Component content) {
        setSizeFull();
        add(content);
    }
}
