package com.wornux.ui.chat;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;

public final class CodeMessageListItem {

  @Getter private final String time;
  @Getter private final String userName;
  private final Set<String> classNames = new LinkedHashSet<>();
  @Getter private String text;
  transient String clientText;
  transient CodeMessageList host;

  public CodeMessageListItem(String text, Instant time, String userName) {
    this.text = text != null ? text : "";
    this.clientText = this.text;
    this.time = time != null ? time.toString() : "";
    this.userName = Objects.requireNonNull(userName, "userName cannot be null");
  }

  public void setText(String text) {
    this.text = text != null ? text : "";
    if (host != null) {
      host.scheduleItemsTextUpdate();
    }
  }

  public void setUserColorIndex() {
    if (host != null) {
      host.scheduleItemsUpdate();
    }
  }

  public String getClassName() {
    return classNames.isEmpty() ? null : String.join(" ", classNames);
  }

  public void addClassNames(String... classNames) {
    boolean changed = false;
    for (String className : classNames) {
      if (className != null && !className.isBlank()) {
        changed |= this.classNames.add(className);
      }
    }
    if (changed && host != null) {
      host.scheduleItemsUpdate();
    }
  }

  void setHost(CodeMessageList host) {
    this.host = host;
  }
}
