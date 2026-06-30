package com.wornux.ui.components;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.wornux.data.entities.identity.Account;
import com.wornux.ui.css.UiCss;

public class ProfileDrawerCard extends Div {

    private final Div menu;
    private final Icon chevron;
    private final NativeButton headerButton;

    public ProfileDrawerCard(Account account, Component themeControl, Runnable logoutAction) {
        UiCss.PROFILE_DRAWER_CARD.addTo(this);

        var menuContent = new Div(themeControl, createLogoutButton(logoutAction));
        UiCss.PROFILE_DRAWER_CARD_MENU_CONTENT.addTo(menuContent);

        menu = new Div(menuContent);
        UiCss.PROFILE_DRAWER_CARD_MENU.addTo(menu);

        chevron = new Icon(VaadinIcon.CHEVRON_UP);
        UiCss.PROFILE_DRAWER_CARD_CHEVRON.addTo(chevron);

        headerButton = createHeaderButton(account);
        add(menu, headerButton);
        closeOnOutsideClick();
    }

    private NativeButton createHeaderButton(Account account) {
        var avatar = new Span(initials(account));
        UiCss.PROFILE_DRAWER_CARD_AVATAR.addTo(avatar);
        avatar.getElement().setAttribute("aria-hidden", "true");

        var name = new Span(displayName(account));
        UiCss.PROFILE_DRAWER_CARD_NAME.addTo(name);

        var email = new Span(safeText(account.getEmail()));
        UiCss.PROFILE_DRAWER_CARD_EMAIL.addTo(email);

        var identity = new Div(name, email);
        UiCss.PROFILE_DRAWER_CARD_IDENTITY.addTo(identity);

        var content = new Div(avatar, identity, chevron);
        UiCss.PROFILE_DRAWER_CARD_HEADER_CONTENT.addTo(content);

        var button = new NativeButton();
        UiCss.PROFILE_DRAWER_CARD_HEADER.addTo(button);
        button.add(content);
        button.setAriaLabel("Abrir opciones de perfil");
        button.getElement().setAttribute("aria-expanded", "false");
        button.addClickListener(_ -> setExpanded(!hasClassName(UiCss.EXPANDED.value()), button));
        return button;
    }

    private Button createLogoutButton(Runnable logoutAction) {
        var button = new Button("Cerrar sesión", new Icon(VaadinIcon.SIGN_OUT));
        button.addThemeVariants(ButtonVariant.TERTIARY);
        UiCss.PROFILE_DRAWER_CARD_LOGOUT.addTo(button);
        button.addClickListener(_ -> logoutAction.run());
        return button;
    }

    @ClientCallable
    public void closeFromOutsideClick() {
        setExpanded(false, headerButton);
    }

    private void closeOnOutsideClick() {
        addAttachListener(_ -> getElement().executeJs("""
                if (this.__profileDrawerOutsideClick) {
                    return;
                }
                this.__profileDrawerOutsideClick = (event) => {
                    if (!this.classList.contains($0)) {
                        return;
                    }
                    const path = event.composedPath ? event.composedPath() : [];
                    if (path.includes(this)) {
                        return;
                    }
                    this.$server.closeFromOutsideClick();
                };
                document.addEventListener('pointerdown', this.__profileDrawerOutsideClick, true);
                """, UiCss.EXPANDED.value()));
        addDetachListener(event -> event.getUI().getPage().executeJs("""
                if ($0.__profileDrawerOutsideClick) {
                    document.removeEventListener('pointerdown', $0.__profileDrawerOutsideClick, true);
                    delete $0.__profileDrawerOutsideClick;
                }
                """, getElement()));
    }

    private void setExpanded(boolean expanded, NativeButton button) {
        if (expanded) {
            UiCss.EXPANDED.addTo(this);
        }
        else {
            getElement().getClassList().remove(UiCss.EXPANDED.value());
        }
        button.getElement().setAttribute("aria-expanded", Boolean.toString(expanded));
        chevron.getElement().setAttribute("icon", expanded ? "vaadin:chevron-down" : "vaadin:chevron-up");
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
