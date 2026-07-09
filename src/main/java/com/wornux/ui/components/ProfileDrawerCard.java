package com.wornux.ui.components;

import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.SvgIcon;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.authorization.ScopeLevel;
import com.wornux.security.authorization.ActiveContextHolder;
import com.wornux.services.context.AvailableContextOption;
import com.wornux.services.context.ContextDiscoveryService;
import com.wornux.services.context.ContextSelectionService;
import com.wornux.ui.css.UiCss;

@Tag("profile-drawer-card")
@JsModule("./shell/profile-drawer-card.ts")
public class ProfileDrawerCard extends Component implements HasComponents {

    private final SvgIcon chevron;

    public ProfileDrawerCard(
            Account account,
            Component themeControl,
            ActiveContextHolder activeContextHolder,
            ContextDiscoveryService contextDiscoveryService,
            ContextSelectionService contextSelectionService,
            Runnable logoutAction) {
        UiCss.PROFILE_DRAWER_CARD.addTo(this);

        var menuContent = new Div(
                createContextControl(account, activeContextHolder, contextDiscoveryService, contextSelectionService),
                themeControl,
                createLogoutButton(logoutAction));
        UiCss.PROFILE_DRAWER_CARD_MENU_CONTENT.addTo(menuContent);

        var menu = new Div(menuContent);
        UiCss.PROFILE_DRAWER_CARD_MENU.addTo(menu);

        chevron = new SvgIcon("/icons/chevron.svg");
        UiCss.PROFILE_DRAWER_CARD_CHEVRON.addTo(chevron);

        var headerButton = createHeaderButton(account, activeContextHolder, contextDiscoveryService);
        add(menu, headerButton);
    }

    private Component createContextControl(
            Account account,
            ActiveContextHolder activeContextHolder,
            ContextDiscoveryService contextDiscoveryService,
            ContextSelectionService contextSelectionService) {
        var options = contextDiscoveryService.discover(account);
        var active = activeContextHolder.current();
        var tenantOptions = options.stream().filter(option -> option.level() == ScopeLevel.TENANT).toList();
        var classOptions = options.stream().filter(option -> option.level() == ScopeLevel.GROUP_CLASS).toList();

        var container = new Div();
        UiCss.PROFILE_DRAWER_CARD_CONTEXT.addTo(container);

        if (!tenantOptions.isEmpty()) {
            container.add(contextSummary("Institución", tenantOptions.getFirst().label()));
        }

        if (classOptions.size() > 1
                || active.map(context -> context.level() == ScopeLevel.GROUP_CLASS).orElse(false)) {
            var selector = new ComboBox<AvailableContextOption>("Clase");
            UiCss.PROFILE_DRAWER_CARD_CONTEXT_SELECT.addTo(selector);
            selector.setItems(classOptions);
            selector.setItemLabelGenerator(AvailableContextOption::label);
            selector.setWidthFull();
            active.flatMap(context -> classOptions.stream().filter(option -> option.matches(context)).findFirst())
                    .ifPresent(selector::setValue);
            selector.addValueChangeListener(event -> {
                if (event.isFromClient() && event.getValue() != null) {
                    var selected = contextSelectionService.select(account, event.getValue());
                    getUI().ifPresent(ui -> ui.navigate(contextSelectionService.defaultRoute(selected)));
                }
            });
            container.add(selector);
        }

        return container;
    }

    private Div contextSummary(String labelText, String valueText) {
        var label = new Span(labelText);
        UiCss.PROFILE_DRAWER_CARD_CONTEXT_LABEL.addTo(label);
        var value = new Span(valueText);
        UiCss.PROFILE_DRAWER_CARD_CONTEXT_VALUE.addTo(value);
        var summary = new Div(label, value);
        UiCss.PROFILE_DRAWER_CARD_CONTEXT_SUMMARY.addTo(summary);
        return summary;
    }

    private NativeButton createHeaderButton(
            Account account,
            ActiveContextHolder activeContextHolder,
            ContextDiscoveryService contextDiscoveryService) {
        var avatar = new Span(initials(account));
        UiCss.PROFILE_DRAWER_CARD_AVATAR.addTo(avatar);
        avatar.getElement().setAttribute("aria-hidden", "true");

        var name = new Span(displayName(account));
        UiCss.PROFILE_DRAWER_CARD_NAME.addTo(name);

        var email = new Span(safeText(account.getEmail()));
        UiCss.PROFILE_DRAWER_CARD_EMAIL.addTo(email);

        var identityChildren = new java.util.ArrayList<Component>(List.of(name, email));
        activeContextHolder.current()
                .flatMap(
                    context -> contextDiscoveryService.discover(account)
                            .stream()
                            .filter(option -> option.matches(context))
                            .findFirst())
                .map(AvailableContextOption::label)
                .ifPresent(contextLabel -> {
                    var context = new Span(contextLabel);
                    UiCss.PROFILE_DRAWER_CARD_CONTEXT_BADGE.addTo(context);
                    identityChildren.add(context);
                });

        var identity = new Div(identityChildren.toArray(Component[]::new));
        UiCss.PROFILE_DRAWER_CARD_IDENTITY.addTo(identity);

        var content = new Div(avatar, identity, chevron);
        UiCss.PROFILE_DRAWER_CARD_HEADER_CONTENT.addTo(content);

        var button = new NativeButton();
        UiCss.PROFILE_DRAWER_CARD_HEADER.addTo(button);
        button.add(content);
        button.setAriaLabel("Abrir opciones de perfil");
        button.getElement().setAttribute("aria-expanded", "false");
        return button;
    }

    private Button createLogoutButton(Runnable logoutAction) {
        var button = new Button("Cerrar sesión", new SvgIcon("/icons/logout.svg"));
        button.addThemeVariants(ButtonVariant.TERTIARY);
        UiCss.PROFILE_DRAWER_CARD_LOGOUT.addTo(button);
        button.addClickListener(_ -> logoutAction.run());
        return button;
    }

    private String displayName(Account account) {
        var fullName = "%s %s".formatted(safeText(account.getFirstName()), safeText(account.getLastName())).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }
        return safeText(account.getEmail());
    }

    private String initials(Account account) {
        var firstName = safeText(account.getFirstName());
        var lastName = safeText(account.getLastName());
        if (!firstName.isBlank() || !lastName.isBlank()) {
            return (firstInitial(firstName) + firstInitial(lastName)).toUpperCase();
        }
        var source = safeText(account.getEmail());
        return source.isBlank() ? "?" : source.substring(0, Math.min(2, source.length())).toUpperCase();
    }

    private String firstInitial(String value) {
        return value.isBlank() ? "" : value.substring(0, 1);
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
