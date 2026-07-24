package com.wornux.ui.training_activity.instruction_review;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.AbstractSinglePropertyField;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.wornux.services.training_activity.instruction_review.InstructionReviewSnapshotDto;
import com.wornux.ui.css.UiCss;

@Tag("instruction-linter-editor")
@JsModule("./training-activity/instruction-review/editor.ts")
public class InstructionLinterEditor extends AbstractSinglePropertyField<InstructionLinterEditor, String> implements HasSize {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    public InstructionLinterEditor() {
        super("value", "", false);
        setWidthFull();
        UiCss.INSTRUCTION_LINTER_EDITOR.addTo(this);
        getElement().setProperty("label", "Instrucciones");
    }

    public void setMinHeight(String minHeight) {
        getElement().getStyle().set("min-height", minHeight);
    }

    public void setReviewSnapshot(InstructionReviewSnapshotDto reviewSnapshot) {
        try {
            getElement().callJsFunction("applyReviewSnapshot", OBJECT_MAPPER.writeValueAsString(reviewSnapshot));
            setReviewing(false);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize instruction review snapshot", exception);
        }
    }

    public void markReviewStale() {
        getElement().setProperty("stale", true);
    }

    public void clearReview() {
        getElement().setProperty("reviewSnapshot", "");
        getElement().setProperty("stale", false);
        setReviewing(false);
    }

    public void resetReviewState() {
        clearReview();
        getElement().setProperty("reviewResetToken", UUID.randomUUID().toString());
    }

    public void setReviewing(boolean reviewing) {
        getElement().setProperty("reviewing", reviewing);
    }

}
