package com.wornux.infrastructure.web;

import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinService;
import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.document.*;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.profile.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.ai.tools.*;
import com.wornux.application.chat.*;
import com.wornux.application.document.*;
import com.wornux.application.profile.*;
import com.wornux.domain.chat.*;
import com.wornux.domain.chat.questions.*;
import com.wornux.domain.document.*;
import com.wornux.domain.profile.*;
import com.wornux.infrastructure.config.*;
import com.wornux.infrastructure.external.docling.*;
import com.wornux.infrastructure.persistence.chat.*;
import com.wornux.infrastructure.persistence.document.*;
import com.wornux.infrastructure.persistence.profile.*;
import com.wornux.presentation.chat.*;
import com.wornux.presentation.chat.ui.*;
import com.wornux.presentation.documentingest.*;
import com.wornux.presentation.documentingest.ui.*;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

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
        .flatMap(
            value -> {
              try {
                return Arrays.stream(new UUID[] {UUID.fromString(value)});
              } catch (IllegalArgumentException exception) {
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
