package com.wornux.ui.auth;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.AccessDeniedException;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.ErrorParameter;
import com.vaadin.flow.router.HasErrorParameter;
import jakarta.annotation.security.PermitAll;

@PermitAll
public class NoAccessErrorView extends Component implements HasErrorParameter<AccessDeniedException> {

    @Override
    public int setErrorParameter(BeforeEnterEvent event, ErrorParameter<AccessDeniedException> parameter) {
        event.forwardTo(NoAccessView.class);
        return 403;
    }
}
