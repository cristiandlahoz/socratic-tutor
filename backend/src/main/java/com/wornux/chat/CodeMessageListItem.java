package com.wornux.chat;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class CodeMessageListItem {

  private final String time;
  private final String userName;
  private final Set<String> classNames = new LinkedHashSet<>();
  private String text;
  private Integer userColorIndex;
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

  public String getText() {
    return text;
  }

  public void appendText(String chunk) {
    if (chunk == null || chunk.isEmpty()) {
      return;
    }
    text += chunk;
    if (host != null) {
      host.scheduleItemsTextUpdate();
    }
  }

  public void setUserColorIndex(Integer userColorIndex) {
    this.userColorIndex = userColorIndex;
    if (host != null) {
      host.scheduleItemsUpdate();
    }
  }

  public String getTime() {
    return time;
  }

  public String getUserName() {
    return userName;
  }

  public Integer getUserColorIndex() {
    return userColorIndex;
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

  public void removeClassNames(String... classNames) {
    boolean changed = false;
    for (String className : classNames) {
      if (className != null) {
        changed |= this.classNames.remove(className);
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
