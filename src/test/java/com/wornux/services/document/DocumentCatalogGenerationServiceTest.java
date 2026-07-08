package com.wornux.services.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.wornux.ai.prompt.PromptResources;
import com.wornux.ui.ingestion.EditableSegmentViewModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

class DocumentCatalogGenerationServiceTest {

    @Test
    void batchesGeneratedSpecificityValidation() {
        var specificityCalls = new AtomicInteger();
        var catalogCalls = new AtomicInteger();
        ChatModel model = prompt -> {
            var userText = prompt.getInstructions().getLast().getText();
            if (userText.contains("Classify each item")) {
                specificityCalls.incrementAndGet();
                return response(specificityResponse(userText));
            }

            catalogCalls.incrementAndGet();
            return response(
                "{" + "\"createCatalog\":true," + "\"label\":\"Binary search loop invariant\","
                        + "\"useWhen\":\"Use for debugging binary search boundary invariants.\"," + "\"aliases\":["
                        + "\"binary search invariant\"," + "\"homework\"," + "\"left right boundary bug\","
                        + "\"midpoint termination proof\"]" + "}");
        };
        var service = new DocumentCatalogGenerationService(model, new PromptResources(new DefaultResourceLoader()));
        ReflectionTestUtils.setField(service, "model", "test-model");

        var catalog = service.generate(
            "binary-search.pdf",
            "Use when students debug binary search boundaries",
            List.of(segment("Binary search maintains inclusive low and high bounds.")));

        assertThat(catalog.label()).isEqualTo("Binary search loop invariant");
        assertThat(catalog.useWhen()).isEqualTo("Use for debugging binary search boundary invariants.");
        assertThat(catalog.aliases())
                .containsExactly("binary search invariant", "left right boundary bug", "midpoint termination proof");
        assertThat(catalogCalls).hasValue(1);
        assertThat(specificityCalls).hasValue(2);
    }

    private static String specificityResponse(String userText) {
        if (userText.contains("professor_usage_instruction")) {
            return "{\"items\":[{" + "\"id\":\"professor_usage_instruction\"," + "\"specific\":true,"
                    + "\"reason\":\"Names binary search boundaries.\"" + "}]}";
        }
        return "{\"items\":["
                + "{\"id\":\"generated_label\",\"specific\":true,\"reason\":\"Names binary search invariant.\"},"
                + "{\"id\":\"generated_use_when\",\"specific\":true,\"reason\":\"Names boundary debugging.\"},"
                + "{\"id\":\"alias_0\",\"specific\":true,\"reason\":\"Specific algorithm concept.\"},"
                + "{\"id\":\"alias_1\",\"specific\":false,\"reason\":\"Generic homework label.\"},"
                + "{\"id\":\"alias_2\",\"specific\":true,\"reason\":\"Specific boundary bug.\"},"
                + "{\"id\":\"alias_3\",\"specific\":true,\"reason\":\"Specific proof topic.\"}" + "]}";
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static EditableSegmentViewModel segment(String content) {
        return new EditableSegmentViewModel("segment-1",
                1,
                "",
                content,
                true,
                false,
                content.length(),
                EditableSegmentViewModel.approximateTokens(content),
                null,
                List.of(),
                List.of(),
                List.of(),
                content,
                "test");
    }
}
