package com.wornux.services.evaluation;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wornux.data.entities.EvaluationRun;
import com.wornux.services.evaluation.EvaluationChatService.AnswerRecord;
import com.wornux.services.evaluation.EvaluationQuestionGenerationService.GeneratedQuestion;

public record EvaluationMemory(EvaluationRun run) {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public List<GeneratedQuestion> getQuestionsAsked() {
        var json = run.getQuestionsAskedJson();
        if (json == null || json.isBlank())
            return Collections.emptyList();
        try {
            return objectMapper.readValue(
                json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, GeneratedQuestion.class));
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize questions from run", e);
        }
    }

    public List<AnswerRecord> getAnswersGiven() {
        var json = run.getAnswersGivenJson();
        if (json == null || json.isBlank())
            return Collections.emptyList();
        try {
            return objectMapper.readValue(
                json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, AnswerRecord.class));
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize answers from run", e);
        }
    }

    public void setQuestionsAsked(List<GeneratedQuestion> questions) {
        try {
            run.setQuestionsAskedJson(objectMapper.writeValueAsString(questions));
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize questions", e);
        }
    }

    public void setAnswersGiven(List<AnswerRecord> answers) {
        try {
            run.setAnswersGivenJson(objectMapper.writeValueAsString(answers));
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize answers", e);
        }
    }

    public void setReportMarkdown(String report) {
        run.setReportMarkdown(report);
    }
}
