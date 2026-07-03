package com.wornux.ui.training_activity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import com.wornux.config.ChatProperties;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.services.training_activity.TrainingAssignmentEvaluationService;
import com.wornux.ui.MainLayout;
import com.wornux.ui.conversation.CodeMessageList;
import com.wornux.ui.conversation.CodeMessageListItem;
import com.wornux.ui.conversation.ConversationComposer;
import com.wornux.ui.conversation.ConversationCss;
import jakarta.annotation.security.PermitAll;

@Route(value = "training-activity/assignments", layout = MainLayout.class)
@PermitAll
public class TrainingAssignmentView extends Composite<Div> implements HasUrlParameter<String> {

    private static final String TUTOR_NAME = "Tutor Socrático";
    private static final String STUDENT_NAME = "Tú";
    private static final String SUBMITTED_MESSAGE = "Evaluation submitted. Your professor can now review the report.";

    private final TrainingAssignmentEvaluationService evaluationService;
    private final CodeMessageList messageList = new CodeMessageList();
    private final ConversationComposer composer;
    private final Div inputShell = new Div();
    private UUID assignmentId;
    private TrainingActivityAssignment assignment;

    public TrainingAssignmentView(
            TrainingAssignmentEvaluationService evaluationService,
            ChatProperties chatProperties) {
        this.evaluationService = evaluationService;

        messageList.setMarkdown(true);
        messageList.setThinkingSpinner(chatProperties.getUi().getThinkingSpinner());
        messageList.setWidthFull();

        composer = new ConversationComposer(
                "Write your answer here...",
                "Write your answer here",
                "Submit answer",
                this::submitAnswer);
        composer.addValueChangeListener(this::updateComposerState);
        composer.setSubmitEnabled(false);

        ConversationCss.COMPOSER.addTo(inputShell);
        inputShell.add(composer);

        var conversationStack = new Div(messageList);
        ConversationCss.THREAD.addTo(conversationStack);

        var historyScroller = new Div(conversationStack);
        historyScroller.setSizeFull();
        ConversationCss.SCROLL_REGION.addTo(historyScroller);

        var chatPane = new Div(historyScroller, inputShell);
        chatPane.setSizeFull();
        ConversationCss.PANE.addTo(chatPane);

        var content = getContent();
        content.setSizeFull();
        ConversationCss.VIEW.addTo(content);
        content.add(chatPane);
    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        try {
            assignmentId = UUID.fromString(parameter);
            assignment = evaluationService.start(assignmentId);
            renderAssignment();
        }
        catch (IllegalArgumentException | SecurityException exception) {
            Notification.show(exception.getMessage());
            UI.getCurrent().navigate("student");
        }
    }

    private void renderAssignment() {
        messageList.setItems(toMessages(assignment));
        if (assignment.getStatus() == TrainingActivityAssignmentStatus.SUBMITTED) {
            inputShell.setVisible(false);
            return;
        }
        inputShell.setVisible(true);
        composer.clear();
        updateComposerState();
    }

    private void submitAnswer() {
        if (assignmentId == null || composer.getValue().trim().isBlank()) {
            return;
        }
        assignment = evaluationService.answer(assignmentId, composer.getValue());
        renderAssignment();
    }

    private void updateComposerState() {
        var enabled = assignment != null && assignment.getStatus() != TrainingActivityAssignmentStatus.SUBMITTED;
        composer.setComposerEnabled(enabled);
        composer.setSubmitEnabled(enabled && !composer.getValue().trim().isBlank());
    }

    private List<CodeMessageListItem> toMessages(TrainingActivityAssignment assignment) {
        var messages = new ArrayList<CodeMessageListItem>();
        var messageTime = assignment.getStartedAt() != null ? assignment.getStartedAt() : assignment.getAssignedAt();
        var offset = 0;

        for (var exchange : evaluationService.readEvaluationTranscript(assignment)) {
            messages.add(assistantMessage(exchange.question(), messageTime.plusMillis(offset++)));
            messages.add(userMessage(exchange.answer(), messageTime.plusMillis(offset++)));
        }

        if (assignment.getCurrentQuestion() != null && !assignment.getCurrentQuestion().isBlank()) {
            messages.add(assistantMessage(assignment.getCurrentQuestion(), messageTime.plusMillis(offset++)));
        }

        if (assignment.getStatus() == TrainingActivityAssignmentStatus.SUBMITTED) {
            messages.add(assistantMessage(SUBMITTED_MESSAGE, submittedMessageTime(assignment, messageTime, offset)));
        }

        return messages;
    }

    private Instant submittedMessageTime(TrainingActivityAssignment assignment, Instant messageTime, int offset) {
        return assignment.getSubmittedAt() != null ? assignment.getSubmittedAt() : messageTime.plusMillis(offset);
    }

    private CodeMessageListItem assistantMessage(String content, Instant createdAt) {
        var item = new CodeMessageListItem(content, createdAt, TUTOR_NAME);
        item.setUserColorIndex();
        ConversationCss.MESSAGE_ASSISTANT.addTo(item);
        return item;
    }

    private CodeMessageListItem userMessage(String content, Instant createdAt) {
        var item = new CodeMessageListItem(content, createdAt, STUDENT_NAME);
        item.setUserColorIndex();
        ConversationCss.MESSAGE_USER.addTo(item);
        return item;
    }
}
