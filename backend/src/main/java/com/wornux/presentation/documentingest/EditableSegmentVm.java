package com.wornux.presentation.documentingest;

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
import com.wornux.infrastructure.web.*;
import com.wornux.presentation.chat.*;
import com.wornux.presentation.chat.ui.*;
import com.wornux.presentation.documentingest.ui.*;
import java.util.List;
import java.util.UUID;

public record EditableSegmentVm(
    UUID id,
    int ordinal,
    String headingPath,
    String content,
    boolean approved,
    boolean edited,
    Integer charCount,
    Integer tokenCount,
    Integer pageNumber,
    List<Integer> pageNumbers,
    List<String> captions,
    List<String> docItems,
    String rawText,
    String chunker) {

  public EditableSegmentVm withContent(String nextContent) {
    String safeContent = nextContent == null ? "" : nextContent;
    boolean changed = !safeContent.equals(content);
    return new EditableSegmentVm(
        id,
        ordinal,
        headingPath,
        safeContent,
        approved,
        edited || changed,
        safeContent.length(),
        approximateTokens(safeContent),
        pageNumber,
        pageNumbers,
        captions,
        docItems,
        rawText,
        chunker);
  }

  public static int approximateTokens(String text) {
    if (text == null || text.isBlank()) {
      return 0;
    }
    return text.trim().split("\\s+").length;
  }
}
