package com.wornux.chat.tools;

//import org.springframework.ai.chat.model.ToolContext;
//import org.springframework.ai.document.Document;
//import org.springframework.ai.tool.annotation.Tool;
//import org.springframework.ai.tool.annotation.ToolParam;
//import org.springframework.ai.vectorstore.SearchRequest;
//import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

//import java.util.List;
//import java.util.regex.Pattern;

@Component
public class RetrieveInformationTool {

//    private static final int SEARCH_TOP_K = 3;
//
//    private final VectorStore vectorStore;
//    private final ToolUsageAuditService toolUsageAuditService;
//
//    public TutorTools(VectorStore vectorStore, ToolUsageAuditService toolUsageAuditService) {
//        this.vectorStore = vectorStore;
//        this.toolUsageAuditService = toolUsageAuditService;
//    }
//
//    @Tool(name = "searchCourseMaterial", description = "Searches indexed course material for the most relevant passages about introductory algorithms and C programming.")
//    public SearchCourseMaterialResult searchCourseMaterial(
//            @ToolParam(description = "The student's question or the concept to search for.")
//            String query,
//            @ToolParam(required = false, description = "Optional topic hint like loops, arrays, or functions.")
//            String topicHint,
//            ToolContext toolContext) {
//        String composedQuery = topicHint == null || topicHint.isBlank() ? query : "%s topic:%s".formatted(query, topicHint);
//        return toolUsageAuditService.audit(
//                "searchCourseMaterial",
//                toolContext,
//                "query_len=%d topic_hint=%s".formatted(query == null ? 0 : query.length(), topicHint == null ? "none" : topicHint),
//                () -> {
//                    List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
//                            .query(composedQuery)
//                            .topK(SEARCH_TOP_K)
//                            .similarityThreshold(0.60)
//                            .build());
//                    List<SearchHit> hits = documents == null ? List.of() : documents.stream()
//                                                                           .map(document -> new SearchHit(
//                                                                                   summarize(document.getText()),
//                                                                                   document.getMetadata().getOrDefault("source", "vector_store").toString(),
//                                                                                   document.getScore()))
//                                                                           .toList();
//                    var result = new SearchCourseMaterialResult(hits, !hits.isEmpty());
//                    return new ToolUsageAuditService.ToolResult<>(
//                            result,
//                            "hits=%d context_found=%s".formatted(hits.size(), result.contextFound()),
//                            new ToolLearningSignal("topic=" + (topicHint == null ? "unknown" : topicHint), !hits.isEmpty(), "retrieval_context"));
//                });
//    }
//
//    private static String summarize(String text) {
//        if (text == null || text.isBlank()) {
//            return "";
//        }
//        String normalized = text.replaceAll("\\s+", " ").trim();
//        return normalized.length() <= 220 ? normalized : normalized.substring(0, 217) + "...";
//    }
//
//
//    public record SearchCourseMaterialResult(List<SearchHit> hits, boolean contextFound) {
//    }
//
//    public record SearchHit(String excerpt, String source, Double score) {
//    }

}
