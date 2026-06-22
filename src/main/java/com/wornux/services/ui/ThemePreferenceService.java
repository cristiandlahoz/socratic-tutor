package com.wornux.services.ui;

import com.vaadin.flow.server.VaadinSession;
import com.wornux.data.enums.ThemePreference;
import org.springframework.stereotype.Service;

@Service
public class ThemePreferenceService {

    private static final String SESSION_KEY = ThemePreferenceService.class.getName() + ".themePreference";

    public ThemePreference getThemePreference() {
        var session = VaadinSession.getCurrent();
        if (session == null) {
            return ThemePreference.SYSTEM;
        }
        Object value = session.getAttribute(SESSION_KEY);
        return value instanceof ThemePreference preference ? preference : ThemePreference.SYSTEM;
    }

    public ThemePreference updateThemePreference(ThemePreference preference) {
        var resolvedPreference = preference == null ? ThemePreference.SYSTEM : preference;
        var session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(SESSION_KEY, resolvedPreference);
        }
        return resolvedPreference;
    }
}
