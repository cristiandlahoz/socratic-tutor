package com.wornux.ui.auth;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "no-access", autoLayout = false)
@PageTitle("No access")
@PermitAll
public class NoAccessView extends VerticalLayout {

    public NoAccessView() {
        addClassName("workspace-view");
        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setDefaultHorizontalComponentAlignment(Alignment.CENTER);

        var title = new H1("No workspace is available yet");
        var description = new Paragraph(
                "This account does not have an active role or class context. Ask a system admin, tenant admin, or professor to invite you.");
        var shell = new Div(title, description);
        shell.addClassName("workspace-card");
        add(shell);
    }
}
