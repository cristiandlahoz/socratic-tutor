package com.wornux.services.evaluation;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;

@Service
public class EvaluationQuestionGenerationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationQuestionGenerationService.class);

    private final ChatModel chatModel;
    private final BeanOutputConverter<QuestionSet> outputConverter = new BeanOutputConverter<>(QuestionSet.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EvaluationQuestionGenerationService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public List<GeneratedQuestion> generateQuestions(String instruction) {
        var prompt = Prompt.builder()
                .messages(new SystemMessage(SYSTEM_PROMPT), new UserMessage(instruction))
                .chatOptions(
                    OllamaChatOptions.builder().temperature(0.2).format(outputConverter.getJsonSchemaMap()).build())
                .build();

        var response = chatModel.call(prompt);
        var content = response.getResult().getOutput().getText();
        var questionSet = outputConverter.convert(content);

        if (questionSet == null || questionSet.questions() == null || questionSet.questions().isEmpty()) {
            throw new IllegalStateException("El modelo no generó preguntas para la instrucción proporcionada");
        }

        log.info(
            "Generated {} questions from instruction ({} chars)",
            questionSet.questions().size(),
            instruction.length());
        return questionSet.questions();
    }

    public String toJson(List<GeneratedQuestion> questions) {
        try {
            return objectMapper.writeValueAsString(questions);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize questions to JSON", e);
        }
    }

    public List<GeneratedQuestion> fromJson(String json) {
        try {
            return objectMapper.readValue(
                json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, GeneratedQuestion.class));
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize questions from JSON", e);
        }
    }

    public record GeneratedQuestion(String questionText, String questionKey) {}

    record QuestionSet(List<GeneratedQuestion> questions) {}

    private static final String SYSTEM_PROMPT =
            """
            Eres un generador de preguntas de diagnóstico académico para un curso de
            Introducción a la Algoritmia (lenguaje C).

            Dada una instrucción, genera preguntas que evalúen comprensión del tema.
            Cada pregunta debe ser clara, específica y requiera razonamiento, no solo
            memorización.

            Reglas:
            - Genera entre 3 y 5 preguntas
            - Cada pregunta debe tener un questionKey único (ej: "q1", "q2", ...)
            - Las preguntas deben seguir un orden pedagógico (de lo básico a lo avanzado)
            - No incluyas respuestas ni opciones múltiples

            Devuelve SOLO JSON con este formato exacto:
            {"questions": [{"questionText": "...", "questionKey": "..."}]}
            """;
}
