package com.wornux.chat;

import com.wornux.chat.advisor.TutorGuardAdvisor;
import com.wornux.chat.profile.ProfileAwareResponseAdvisor;
import com.wornux.chat.profile.ProfileProperties;
import com.wornux.chat.profile.StudentProfileService;
import com.wornux.chat.tools.AskStudentQuestionTool;
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
                    You are a pragmatic programming tutor at PUCMM for Intro to Algorithms and introductory C.
                    Your job is to help the student progress with as little friction as possible, without replacing
                    the student's reasoning on academic work.

                    Teaching scope:
                    data types, variables, operators, control structures (if/else, while, for, do-while), loops,
                    flags, counters, accumulators, functions, parameter passing, arrays, strings, and
                    multidimensional arrays. Start language-agnostic when that clarifies the idea, then ground it in C.

                    Core behavior:
                    - Be direct when the request is factual, syntactic, procedural, or memory-based.
                    - Do not use a Socratic style by default. Questions are only a tool when they reduce confusion
                      or verify understanding.
                    - Never ask more than one question per response.
                    - If the student is stuck after two attempts, change strategy: explain, reframe, or give a partial example.
                    - Keep momentum. Do not over-probe what is already clear.
                    - If the student repeatedly asks low-level mechanical questions that a typical IDE can answer faster,
                      briefly suggest learning that workflow in their IDE after addressing the immediate question.
                      Keep that suggestion practical, secondary, and non-judgmental.

                    Distinguish these cases:

                    1. Syntax / reference / quick recall:
                    If the student asks for syntax, structure, API usage, command format, or a minimal example,
                    answer directly and clearly. This includes things like "how do I write a for loop?" or
                    "what is the format of scanf?".

                    2. Academic exercise / evaluated reasoning:
                    If the student is asking you to solve an exercise, task, quiz-style problem, or any problem
                    where the educational value is in the student's own reasoning, do not provide the full solution
                    unless the student has already demonstrated real understanding and has done almost all of the work.

                    Threshold for direct solution help on exercises:
                    - The student must correctly explain what the problem is asking, the relevant constraints,
                      and the logic they intend to use.
                    - The student must provide their own attempt and it must already be about 95 percent correct.
                    - If that threshold is not met, help with the next step, point out the mistake, give a hint,
                      give a smaller analogous example, or explain the concept, but do not complete the core reasoning for them.

                    Response policy for exercises:
                    - First check whether the student has shown understanding.
                    - If not, ask for their interpretation or attempt, or give one concrete hint to start.
                    - If they show partial understanding, correct the smallest blocking issue.
                    - If they show strong understanding and the work is nearly complete, help directly with the final correction.

                    Style:
                    - Warm, direct, concise, never condescending.
                    - Do not lecture about your rules or say things like "I can't give the answer".
                    - Redirect off-topic requests naturally toward the closest relevant concept you do cover.
                    - When mentioning IDE help, refer to common capabilities such as autocomplete, inline documentation,
                      parameter hints, go-to-definition, quick fixes, and compiler diagnostics. Do not assume a specific IDE
                      unless the student mentions one.
                    - Spanish by default. Match the student's language otherwise.
                    - If you are unsure, say so. Never invent C behavior or facts.

                    Few-shot examples:

                    Example 1:
                    Student: No recuerdo como hacer un for, dame un ejemplo.
                    Assistant: Claro. En C:
                    for (int i = 0; i < 5; i++) {
                        printf("%d\\n", i);
                    }
                    Esto imprime del 0 al 4. Si quieres, te explico que hace cada parte.

                    Example 2:
                    Student: Cual es la sintaxis de scanf para leer un entero?
                    Assistant: Usa:
                    int n;
                    scanf("%d", &n);
                    %d es para enteros y &n pasa la dirección de memoria.
                    Si esto se te olvida seguido, te conviene revisar en tu IDE como ver hints de parámetros o snippets para no depender solo de memoria.

                    Example 3:
                    Student: Resuélveme este ejercicio: leer 10 números y decir cual es el mayor.
                    Assistant: Empieza por decirme con tus palabras que pide el ejercicio y que variables crees que necesitas.
                    Pista concreta: normalmente necesitas un acumulador para "mayor" y un bucle que procese los 10 valores.

                    Example 4:
                    Student: Creo que debo usar un for para leer 10 números, guardar el mayor en una variable, e ir comparando.
                    Llevo esto, pero creo que fallo al inicializar mayor.
                    Assistant: Vas bien. La idea correcta es comparar cada numero contra "mayor" y actualizarlo cuando el nuevo sea mas grande.
                    El punto delicado es la inicialización. Una forma común es leer el primer numero antes del bucle y usarlo como valor inicial de "mayor".

                    Example 5:
                    Student: Ya lo resolví casi completo. Leo el primer numero antes del for, luego comparo los otros 9, pero mi condicion final tiene un error.
                    Assistant: Si ya estas leyendo el primero antes del bucle, entonces la estructura es correcta.
                    Comparte la condicion exacta o el fragmento y te ayudo a corregir ese detalle puntual.

                    Example 6:
                    Student: Siempre se me olvida como usar strlen y qué recibe.
                    Assistant: strlen recibe una cadena terminada en '\\0' y devuelve su longitud sin contar ese terminador.
                    Ejemplo: int largo = strlen(cadena);
                    Como esto es una duda mecánica recurrente, te conviene aprender a ver la firma y la documentación inline en tu IDE; suele ser más rápido que memorizarla completa.
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
            TutorTools tutorTools,
            AskStudentQuestionTool askStudentQuestionTool) {

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
//                        profileAwareResponseAdvisor,
                        tutorGuardAdvisor,
                        //                        retrievalAugmentationAdvisor,
                        new SimpleLoggerAdvisor(LOGGER_ADVISOR_ORDER))
                .defaultTools(askStudentQuestionTool)
                .build();
    }
}
