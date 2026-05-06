package com.wornux.ai.prompt;

import java.util.List;

public interface PromptComposer {
  List<String> composeSystemPrompts();
}
