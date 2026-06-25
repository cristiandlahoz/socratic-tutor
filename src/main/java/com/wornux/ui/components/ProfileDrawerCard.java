package com.wornux.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.wornux.data.entities.identity.Account;

public class ProfileDrawerCard extends Div {

    private final Div menu;
    private final Icon chevron;

    public ProfileDrawerCard(Account account, Component themeControl, Runnable logoutAction) {
        addClassName("profile-drawer-card");

        menu = new Div(themeControl, createLogoutButton(logoutAction));
        menu.addClassName("profile-drawer-card__menu");
        menu.setVisible(false);

        chevron = new Icon(VaadinIcon.CHEVRON_UP);
        chevron.addClassName("profile-drawer-card__chevron");

        add(createHeaderButton(account), menu);
    }

    private NativeButton createHeaderButton(Account account) {
        var avatar = new Span(initials(account));
        avatar.addClassName("profile-drawer-card__avatar");
        avatar.getElement().setAttribute("aria-hidden", "true");

        var name = new Span(displayName(account));
        name.addClassName("profile-drawer-card__name");

        var email = new Span(safeText(account.getEmail()));
        email.addClassName("profile-drawer-card__email");

        var identity = new Div(name, email);
        identity.addClassName("profile-drawer-card__identity");

        var content = new Div(avatar, identity, chevron);
        content.addClassName("profile-drawer-card__header-content");

        var button = new NativeButton();
        button.addClassName("profile-drawer-card__header");
        button.add(content);
        button.setAriaLabel("Abrir opciones de perfil");
        button.getElement().setAttribute("aria-expanded", "false");
        button.addClickListener(_ -> setExpanded(!menu.isVisible(), button));
        return button;
    }

    private Button createLogoutButton(Runnable logoutAction) {
        var button = new Button("Cerrar sesión", new Icon(VaadinIcon.SIGN_OUT));
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.addClassName("profile-drawer-card__logout");
        button.addClickListener(_ -> logoutAction.run());
        return button;
    }

    private void setExpanded(boolean expanded, NativeButton button) {
        menu.setVisible(expanded);
        button.getElement().setAttribute("aria-expanded", Boolean.toString(expanded));
        chevron.getElement().setAttribute("icon", expanded ? "vaadin:chevron-down" : "vaadin:chevron-up");
    }

    private String displayName(Account account) {
        var fullName = "%s %s".formatted(safeText(account.getFirstName()), safeText(account.getLastName())).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }
        if (!safeText(account.getUsername()).isBlank()) {
            return account.getUsername();
        }
        return safeText(account.getEmail());
    }

    private String initials(Account account) {
        var firstName = safeText(account.getFirstName());
        var lastName = safeText(account.getLastName());
        if (!firstName.isBlank() || !lastName.isBlank()) {
            return (firstInitial(firstName) + firstInitial(lastName)).toUpperCase();
        }
        var source = safeText(account.getUsername()).isBlank() ? safeText(account.getEmail()) : account.getUsername();
        return source.isBlank() ? "?" : source.substring(0, Math.min(2, source.length())).toUpperCase();
    }

    private String firstInitial(String value) {
        return value.isBlank() ? "" : value.substring(0, 1);
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
