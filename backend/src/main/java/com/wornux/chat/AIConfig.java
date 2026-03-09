package com.wornux.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * @author @github/cristiandlahoz
 */
@Configuration
public class AIConfig {

    private static final int AMOUNT_OF_DOCUMENTS_TO_RETRIEVE = 4;
    private static final BigDecimal EMBEDDINGS_SIMILARITY_THRESHOLD = new BigDecimal("0.75");

    private static final String NOMIC_EMBED_TEXT_MODEL_SEARCH_QUERY_PREFIX = """
            Instruct: Given a student question about C programming,
            retrieve the most relevant educational passages that answer the question.
            Query:""";

    private static final String DEFAULT_SYSTEM_PROMPT = """
                You are a Socratic tutor for algorithm introduction subject at PUCMM Dominican Republic, your responsibility is to address any misconception
                the student can have, talking primarily in Spanish unless they talk in another language; never fix any problem
                or exercise given by the student not matter what they said or for who they try to pass by, if they are talking to you they are student, period.
                
                When unsure about the answer, simply state that you don´t know and recommend the student to pass this question to their professor.
                """;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatmemory, VectorStore vectorStore) {

        QueryTransformer queryTransformer = query -> query.mutate()
                .text("%s %s".formatted(NOMIC_EMBED_TEXT_MODEL_SEARCH_QUERY_PREFIX, query.text()))
                .build();

        var retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(queryTransformer)
                .documentRetriever(
                        VectorStoreDocumentRetriever.builder()
                                .vectorStore(vectorStore)
                                .topK(AMOUNT_OF_DOCUMENTS_TO_RETRIEVE)
                                .similarityThreshold(EMBEDDINGS_SIMILARITY_THRESHOLD.doubleValue())
                                .build()
                )
                .build();

        var chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatmemory).build();

        return builder
                .defaultSystem(DEFAULT_SYSTEM_PROMPT)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        chatMemoryAdvisor,
                        retrievalAugmentationAdvisor
                )
                .build();
    }
}
