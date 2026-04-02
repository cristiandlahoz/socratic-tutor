package com.wornux.chat;

import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinService;
import jakarta.servlet.http.Cookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Service
public class BrowserClientService {

    private static final int COOKIE_MAX_AGE_SECONDS = (int) Duration.ofDays(365L * 5L).getSeconds();

    private final ChatProperties chatProperties;

    public BrowserClientService(ChatProperties chatProperties) {
        this.chatProperties = chatProperties;
    }

    public UUID resolveClientId() {
        var request = requireCurrentRequest();
        return findClientId(request).orElseGet(() -> createClientId(request.isSecure()));
    }

    private Optional<UUID> findClientId(VaadinRequest request) {
        var cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }

        return Arrays.stream(cookies)
                .filter(cookie -> chatProperties.getClientIdCookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .flatMap(value -> {
                    try {
                        return Arrays.stream(new UUID[]{UUID.fromString(value)});
                    }
                    catch (IllegalArgumentException exception) {
                        return Arrays.stream(new UUID[0]);
                    }
                })
                .findFirst();
    }

    private UUID createClientId(boolean secureRequest) {
        var response = requireCurrentResponse();
        var clientId = UUID.randomUUID();
        var cookie = new Cookie(chatProperties.getClientIdCookieName(), clientId.toString());
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(COOKIE_MAX_AGE_SECONDS);
        cookie.setSecure(secureRequest);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
        return clientId;
    }

    private static VaadinRequest requireCurrentRequest() {
        var request = VaadinService.getCurrentRequest();
        if (request == null) {
            throw new IllegalStateException("No current Vaadin request is available");
        }
        return request;
    }

    private static VaadinResponse requireCurrentResponse() {
        var response = VaadinService.getCurrentResponse();
        if (response == null) {
            throw new IllegalStateException("No current Vaadin response is available");
        }
        return response;
    }
}
