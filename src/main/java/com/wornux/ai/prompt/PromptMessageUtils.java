package com.wornux.ai.prompt;

import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;

public final class PromptMessageUtils {

  private PromptMessageUtils() {}

  public static String extractLastUserText(Prompt prompt) {
    List<Message> messages = prompt.getInstructions();
    for (int index = messages.size() - 1; index >= 0; index--) {
      Message message = messages.get(index);
      if (message.getMessageType() == MessageType.USER) {
        return message.getText();
      }
    }
    return "";
  }
}
