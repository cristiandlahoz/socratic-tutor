package com.wornux.ui.auth;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.services.context.AvailableContextOption;
import com.wornux.services.context.ContextDiscoveryService;
import com.wornux.services.context.ContextSelectionService;
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.ui.css.UiCss;
import jakarta.annotation.security.PermitAll;

@Route(value = "select-context", autoLayout = false)
@PageTitle("Seleccionar contexto")
@PermitAll
public class ContextSelectionView extends VerticalLayout {

    public ContextSelectionView(
            AuthenticatedAccountService authenticatedAccountService,
            ContextDiscoveryService contextDiscoveryService,
            ContextSelectionService contextSelectionService) {
        UiCss.ONBOARDING_VIEW.addTo(this);
        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setDefaultHorizontalComponentAlignment(Alignment.CENTER);

        var account = authenticatedAccountService.requireCurrentAccount();
        var title = new H1("Elige dónde trabajar");
        var description = new Paragraph("Tu navegación y permisos se ajustarán al contexto seleccionado.");
        var cards = new Div();
        UiCss.SIDEBAR_ACTIONS_LIST.addTo(cards);
        contextDiscoveryService.discover(account).forEach(option -> cards.add(createCard(account, option, contextSelectionService)));

        var shell = new Div(title, description, cards);
        UiCss.ONBOARDING_TERMINAL_CARD.addTo(shell);
        add(shell);
    }

    private Button createCard(com.wornux.data.entities.identity.Account account, AvailableContextOption option, ContextSelectionService contextSelectionService) {
        var button = new Button(option.label());
        button.setTooltipText(option.subtitle());
        button.addClickListener(_ -> {
            var selected = contextSelectionService.select(account, option);
            UI.getCurrent().navigate(contextSelectionService.defaultRoute(selected));
        });
        return button;
    }
}
