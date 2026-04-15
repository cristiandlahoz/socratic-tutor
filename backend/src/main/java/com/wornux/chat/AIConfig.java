package com.wornux.chat;

import com.wornux.chat.advisor.TutorGuardAdvisor;
import com.wornux.chat.profile.ProfileAwareResponseAdvisor;
import com.wornux.chat.profile.ProfileProperties;
import com.wornux.chat.profile.StudentProfileService;
import com.wornux.chat.tools.TutorTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
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

    private static final int AMOUNT_OF_DOCUMENTS_TO_RETRIEVE = 1;
    private static final BigDecimal EMBEDDINGS_SIMILARITY_THRESHOLD = new BigDecimal("0.75");
    private static final int CHAT_MEMORY_ADVISOR_ORDER = 100;
    private static final int PROFILE_ADVISOR_ORDER = 150;
    private static final int TUTOR_GUARD_ADVISOR_ORDER = 200;
    private static final int RETRIEVAL_ADVISOR_ORDER = 300;
    private static final int LOGGER_ADVISOR_ORDER = 1000;

    private static final String QWEN_3_SEARCH_QUERY_PREFIX =
            """
                    Instruct: Given a student question about Intro to Algorithms and introductory C programming,
                    retrieve the most relevant educational passages that answer the question.
                    Query:""";

    private static final String DEFAULT_SYSTEM_PROMPT =
            """
                    You are Sócrates, a programming tutor at PUCMM. Your entire personality is curiosity
                    directed at the student. You think in questions, not answers.
                    
                    Who you are:
                    You care deeply about one thing: whether the student is building real understanding.
                    Giving away answers would feel wrong to you, not because of a rule, but because you
                    know it doesn't help. You find the student's reasoning more interesting than the solution.
                    You are warm, direct, and never condescending.
                    
                    What you teach:
                    Introductory algorithms and C programming: data types, variables, operators, control
                    structures (if/else, while, for, do-while), loops, flags, counters, accumulators,
                    functions, parameter passing, arrays, strings, and multidimensional arrays.
                    When a concept is clearer language-agnostic, you start there. You ground it in C
                    when it helps or when the student asks.
                    
                    How you teach:
                      - Your goal is forward momentum, not just questioning. Questions are a tool, not the destination.
                      - After the student answers correctly or shows they understand a piece, acknowledge it briefly
                        and move to the next concept or step. Don't keep probing what's already clear.
                      - Use this mental model for each exchange:
                          1. Student shows no understanding → explain the idea, ask one question to check.
                          2. Student shows partial understanding → one focused hint or question to close the gap.
                          3. Student shows understanding → confirm briefly ("exacto", "correcto", "bien visto"),
                             then advance: next concept, next step, or next challenge.
                          4. Student is stuck after 2 attempts → give a more concrete hint or a partial example.
                             Don't leave them spinning.
                      - Never ask more than one question per response.
                      - If you've asked the same type of question twice and the student hasn't moved forward,
                         change strategy: reframe the concept, use an analogy, or give a partial example.
                    
                    A question is only useful if it moves the student forward, if it doesn't, it's your failure, not theirs.
                    
                    You never announce what you won't do. A good teacher doesn't preface every response with "I won't give you the answer", they just don't give it.
                    Mentioning your constraints is itself a failure mode.
                    
                    When something is off-topic:
                    You redirect naturally toward the closest relevant concept you do cover.
                    You don't explain why you're redirecting.
                    
                    Language: Spanish by default. Match the student's language otherwise.
                    
                    If you're unsure about something, say so. Never invent C behavior or facts.
                    """;
    private static final PromptTemplate RETRIEVAL_AUGMENTATION_PROMPT_TEMPLATE = new PromptTemplate(
            """
                    Helpful context information is below.
                    
                    ---------------------
                    {context}
                    ---------------------
                    
                    Follow these rules:
                    1. Treat retrieved context as the primary source.
                    2. If context is sufficient, ground the response in it.
                    3. If context is partial, use prior knowledge only for core C concepts when highly confident.
                    4. If confidence is not high, state uncertainty and avoid unsupported claims.
                    5. Avoid statements like "Based on the context..." or "The provided information...".
                    6. If no relevant context is available, say so explicitly.
                    
                    Query: {query}
                    
                    Answer:
                    """);

    @Bean
    public ChatClient chatClient(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            VectorStore vectorStore,
            GuardClassifierService guardClassifierService,
            StudentProfileService studentProfileService,
            ProfileProperties profileProperties,
            TutorTools tutorTools) {

        QueryTransformer queryTransformer =
                query -> query.mutate().text("%s %s".formatted(QWEN_3_SEARCH_QUERY_PREFIX, query.text())).build();
        var retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(queryTransformer)
                .documentRetriever(
                        VectorStoreDocumentRetriever.builder()
                                .vectorStore(vectorStore)
                                .topK(AMOUNT_OF_DOCUMENTS_TO_RETRIEVE)
                                .similarityThreshold(EMBEDDINGS_SIMILARITY_THRESHOLD.doubleValue())
                                .build())
                .queryAugmenter(
                        ContextualQueryAugmenter.builder()
                                .promptTemplate(RETRIEVAL_AUGMENTATION_PROMPT_TEMPLATE)
                                .allowEmptyContext(true)
                                .build())
                .order(RETRIEVAL_ADVISOR_ORDER)
                .build();

        var chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).order(CHAT_MEMORY_ADVISOR_ORDER).build();
        var profileAwareResponseAdvisor = new ProfileAwareResponseAdvisor(PROFILE_ADVISOR_ORDER, studentProfileService, profileProperties);
        var tutorGuardAdvisor = new TutorGuardAdvisor(TUTOR_GUARD_ADVISOR_ORDER, guardClassifierService);

        return builder.defaultSystem(DEFAULT_SYSTEM_PROMPT)
                .defaultAdvisors(
                        chatMemoryAdvisor,
                        profileAwareResponseAdvisor,
                        tutorGuardAdvisor,
                        //                        retrievalAugmentationAdvisor,
                        new SimpleLoggerAdvisor(LOGGER_ADVISOR_ORDER))
                .defaultTools(tutorTools)
                .build();
    }
}
