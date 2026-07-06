package com.wornux.ai.advisor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.wornux.ai.prompt.PromptUtil;
import com.wornux.ai.tools.ToolContextKeys;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.jdbc.core.simple.JdbcClient;
import reactor.core.publisher.Flux;

public class DynamicContextManagementAdvisor implements CallAdvisor, StreamAdvisor {

    private static final String NAME = "dynamic-context-management-advisor";
    private static final String CATALOG_PARAMETER = "catalog";

    private static final String CATALOG_QUERY =
            """
            select distinct on (metadata ->> 'ingestionId')
                   metadata -> 'catalog' ->> 'label' as label,
                   metadata -> 'catalog' ->> 'useWhen' as use_when,
                   coalesce((
                       select string_agg(alias.value, ', ' order by alias.value)
                       from jsonb_array_elements_text(metadata::jsonb -> 'catalog' -> 'aliases') as alias(value)
                   ), '') as aliases
            from grounding_vector_store
            where metadata ->> 'groupClassId' = :groupClassId
              and metadata ->> 'status' = 'READY'
              and metadata::jsonb -> 'catalog' is not null
              and coalesce(metadata -> 'catalog' ->> 'label', '') <> ''
              and coalesce(metadata -> 'catalog' ->> 'useWhen', '') <> ''
            order by metadata ->> 'ingestionId', id
            """;

    private final int order;
    private final JdbcClient jdbcClient;

    public DynamicContextManagementAdvisor(int order, JdbcClient jdbcClient) {
        this.order = order;
        this.jdbcClient = jdbcClient;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, @NonNull CallAdvisorChain chain) {
        return chain.nextCall(withDynamicContext(request));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, @NonNull StreamAdvisorChain chain) {
        return chain.nextStream(withDynamicContext(request));
    }

    private ChatClientRequest withDynamicContext(ChatClientRequest request) {
        return request.mutate().prompt(renderPrompt(request)).build();
    }

    private Prompt renderPrompt(ChatClientRequest request) {
        return request.prompt().augmentSystemMessage(system -> {
            var text = system.getText();
            if (isBlank(text)) {
                return system;
            }
            return system.mutate().text(renderTemplate(text, request.context())).build();
        });
    }

    private String renderTemplate(String template, Map<String, @Nullable Object> context) {
        return render(template, dynamicVariables(context)).trim();
    }

    private Map<String, Object> dynamicVariables(Map<String, @Nullable Object> context) {
        return Map.of(CATALOG_PARAMETER, catalogBlock(context).orElse(""));
    }

    private String render(String template, Map<String, Object> variables) {
        return PromptUtil.render(template, variables);
    }

    private Optional<String> catalogBlock(Map<String, @Nullable Object> context) {
        return groupClassId(context).map(this::catalogEntries)
                .filter(entries -> !entries.isEmpty())
                .map(this::formatCatalog);
    }

    private List<CatalogEntry> catalogEntries(UUID groupClassId) {
        return jdbcClient.sql(CATALOG_QUERY)
                .param("groupClassId", groupClassId.toString())
                .query(this::catalogEntry)
                .list();
    }

    private String formatCatalog(List<CatalogEntry> entries) {
        return """
               <available_course_material>
               Use this catalog only to decide whether to call searchCourseMaterial. Do not answer from this catalog itself.
               Stored material currently available:
               %s</available_course_material>"""
                .formatted(formatCatalogEntries(entries));
    }

    private String formatCatalogEntries(List<CatalogEntry> entries) {
        var builder = new StringBuilder();
        for (var entry : entries) {
            builder.append(formatCatalogEntry(entry));
        }
        return builder.toString();
    }

    private String formatCatalogEntry(CatalogEntry entry) {
        return "- %s: %s%s%n".formatted(entry.label(), entry.useWhen(), aliases(entry));
    }

    private String aliases(CatalogEntry entry) {
        return isBlank(entry.aliases()) ? "" : " Aliases: %s.".formatted(entry.aliases());
    }

    private CatalogEntry catalogEntry(ResultSet rs, int rowNumber) throws SQLException {
        return new CatalogEntry(nonNullString(rs.getString("label")),
                nonNullString(rs.getString("use_when")),
                nonNullString(rs.getString("aliases")));
    }

    private Optional<UUID> groupClassId(Map<String, @Nullable Object> context) {
        return parseUuid(context.get(ToolContextKeys.GROUP_CLASS_ID));
    }

    private Optional<UUID> parseUuid(@Nullable Object value) {
        if (value == null || isBlank(String.valueOf(value))) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(String.valueOf(value)));
        }
        catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private static String nonNullString(@Nullable String value) {
        return value == null ? "" : value;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrder() {
        return order;
    }

    private record CatalogEntry(String label, String useWhen, String aliases) {}
}
