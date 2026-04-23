package com.wornux.documentingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

class DocumentCatalogServiceTest {

  @Test
  void analyzeOrFallback_returns_stale_fallback_when_model_fails() {
    var chatModel = mock(ChatModel.class);
    when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenThrow(new IllegalStateException("model down"));

    var service = new DocumentCatalogService(chatModel, new DocumentIngestionProperties());

    var analysis = service.analyzeOrFallback("loops.pdf", "# Ciclos\n\nContenido sobre while y for.");

    assertThat(analysis.stale()).isTrue();
    assertThat(analysis.entry().title()).isEqualTo("loops.pdf");
    assertThat(analysis.entry().topic()).isEqualTo("Ciclos");
  }
}
