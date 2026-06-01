package com.wornux.ui.components;

import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.SvgIcon;

/**
 * Shared drawer toggle for the application shell.
 *
 * @author cristiandlahoz
 */
public class ShellDrawerToggle extends DrawerToggle {

    public ShellDrawerToggle(String className, String ariaLabel) {
        setIcon(new SvgIcon("/icons/toggle.svg"));
        addThemeVariants(ButtonVariant.TERTIARY);
        addClassName(className);
        setAriaLabel(ariaLabel);
    }
}
