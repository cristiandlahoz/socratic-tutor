package com.wornux.ui.components.chat;

import java.util.function.Consumer;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.internal.JacksonUtils;
import com.vaadin.flow.shared.Registration;
import com.wornux.dtos.chat.questions.StudentQuestionResponse;
import com.wornux.dtos.chat.questions.StudentQuestionSet;
import com.wornux.ui.css.UiCss;

@Tag("student-question-panel")
@JsModule("./conversation/student-question-panel.ts")
public class StudentQuestionPanel extends Component {

    private transient Consumer<StudentQuestionResponse> submitHandler = _ -> {};

    public StudentQuestionPanel() {
        UiCss.CONVERSATION_QUESTION.addTo(this);
        addSubmitQuestionResponseListener(event -> submitHandler.accept(event.getResponse()));
    }

    public void setQuestionSet(StudentQuestionSet questionSet) {
        if (questionSet == null) {
            getElement().setPropertyJson("questionSet", JacksonUtils.nullNode());
            return;
        }
        getElement().setPropertyJson("questionSet", JacksonUtils.beanToJson(questionSet));
    }

    public void setSubmitHandler(Consumer<StudentQuestionResponse> submitHandler) {
        this.submitHandler = submitHandler == null ? _ -> {} : submitHandler;
    }

    public void setSubmitting(boolean submitting) {
        getElement().setProperty("submitting", submitting);
    }

    private Registration addSubmitQuestionResponseListener(
            ComponentEventListener<SubmitQuestionResponseEvent> listener) {
        return addListener(SubmitQuestionResponseEvent.class, listener);
    }

    @DomEvent("submit-question-response")
    public static final class SubmitQuestionResponseEvent extends ComponentEvent<StudentQuestionPanel> {

        private final StudentQuestionResponse response;

        public SubmitQuestionResponseEvent(
                StudentQuestionPanel source,
                boolean fromClient,
                @EventData("event.detail.responseJson") String responseJson) {
            super(source, fromClient);
            this.response = JacksonUtils.readToObject(JacksonUtils.readTree(responseJson), StudentQuestionResponse.class);
        }

        public StudentQuestionResponse getResponse() {
            return response;
        }
    }
}
