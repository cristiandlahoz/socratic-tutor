package com.wornux.ai.prompt;

import java.util.Map;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;

public final class PromptUtil {

    private static final StTemplateRenderer RENDERER = StTemplateRenderer.builder()
            .startDelimiterToken('$')
            .endDelimiterToken('$')
            .build();

    private PromptUtil() {}

    public static PromptTemplate create(String template) {
        return PromptTemplate.builder()
                .renderer(RENDERER)
                .template(template)
                .build();
    }

    public static String render(String template, Map<String, Object> variables) {
        return create(template).render(variables);
    }
}
