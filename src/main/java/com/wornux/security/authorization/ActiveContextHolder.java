package com.wornux.security.authorization;

import java.util.Optional;

import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

@Component
public class ActiveContextHolder {

    private static final Class<ActiveContext> ATTRIBUTE_KEY = ActiveContext.class;
    private final ThreadLocal<ActiveContext> fallbackContext = new ThreadLocal<>();

    public Optional<ActiveContext> current() {
        var session = VaadinSession.getCurrent();
        if (session != null) {
            return Optional.ofNullable(session.getAttribute(ATTRIBUTE_KEY));
        }
        return Optional.ofNullable(fallbackContext.get());
    }

    public void set(ActiveContext context) {
        var session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(ATTRIBUTE_KEY, context);
            return;
        }
        fallbackContext.set(context);
    }

    public void clear() {
        var session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(ATTRIBUTE_KEY, null);
            return;
        }
        fallbackContext.remove();
    }
}
