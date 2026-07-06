package com.wornux.ui.auth;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.wornux.ui.css.UiCss;
import jakarta.annotation.security.PermitAll;

@Route(value = "no-access", autoLayout = false)
@PageTitle("No access")
@PermitAll
public class NoAccessView extends VerticalLayout {

    public NoAccessView(AuthenticationContext authenticationContext) {
        UiCss.ONBOARDING_VIEW.addTo(this);
        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setDefaultHorizontalComponentAlignment(Alignment.CENTER);

        var title = new H1("No tienes acceso a este espacio");
        var description = new Paragraph(
                "Tu cuenta no tiene un contexto activo disponible, o el contexto actual no incluye el permiso requerido para esta ruta.");
        var logout = new Button("Cerrar sesión", _ -> authenticationContext.logout());
        var shell = new Div(title, description, logout);
        UiCss.ONBOARDING_TERMINAL_CARD.addTo(shell);
        add(shell);
    }
}
