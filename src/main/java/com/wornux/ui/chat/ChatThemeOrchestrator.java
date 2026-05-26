package com.wornux.ui.chat;

import com.vaadin.flow.component.UI;
import com.wornux.data.enums.ThemePreference;
import org.springframework.stereotype.Component;

@Component
public class ChatThemeOrchestrator {

  public void applyThemePreference(ThemePreference preference) {
    var ui = UI.getCurrent();
    if (ui == null) {
      return;
    }

    var storageValue = (preference == null ? ThemePreference.SYSTEM : preference).storageValue();
    ui.getElement().setAttribute("data-theme-preference", storageValue);
    ui.getPage()
        .executeJs(
            """
            document.documentElement.setAttribute('data-theme-preference', $0);
            document.body?.setAttribute('data-theme-preference', $0);
            """,
            storageValue);
  }
}
