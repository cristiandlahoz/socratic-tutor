package com.wornux.ui.components.ingestion;

import java.util.List;
import java.util.function.BiConsumer;
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
import com.wornux.ui.css.UiCss;
import com.wornux.ui.ingestion.EditableSegmentViewModel;

@Tag("document-segment-editor-list")
@JsModule("./ingestion/document-segment-editor-list.ts")
public class DocumentSegmentEditorList extends Component {

    private BiConsumer<String, String> segmentChangeListener = (_, _) -> {};
    private Consumer<String> segmentDeleteListener = _ -> {};

    public DocumentSegmentEditorList() {
        setId("document-ingestion-segment-editor-list");
        UiCss.DOCUMENT_INGEST_SEGMENT_SHELL.addTo(this);
        addSegmentContentChangedListener(
            event -> segmentChangeListener.accept(event.getSegmentId(), event.getContent()));
        addSegmentDeleteRequestedListener(event -> segmentDeleteListener.accept(event.getSegmentId()));
    }

    public void setSegmentChangeListener(BiConsumer<String, String> segmentChangeListener) {
        this.segmentChangeListener = segmentChangeListener == null ? (_, _) -> {} : segmentChangeListener;
    }

    public void setSegmentDeleteListener(Consumer<String> segmentDeleteListener) {
        this.segmentDeleteListener = segmentDeleteListener == null ? _ -> {} : segmentDeleteListener;
    }

    public void setSegments(List<EditableSegmentViewModel> segments) {
        var safeSegments = segments == null ? List.<EditableSegmentViewModel>of() : List.copyOf(segments);
        getElement().setPropertyJson("segments", JacksonUtils.listToJson(safeSegments));
    }

    private Registration addSegmentContentChangedListener(
            ComponentEventListener<SegmentContentChangedEvent> listener) {
        return addListener(SegmentContentChangedEvent.class, listener);
    }

    private Registration addSegmentDeleteRequestedListener(
            ComponentEventListener<SegmentDeleteRequestedEvent> listener) {
        return addListener(SegmentDeleteRequestedEvent.class, listener);
    }

    @DomEvent("segment-content-changed")
    public static final class SegmentContentChangedEvent extends ComponentEvent<DocumentSegmentEditorList> {

        private final String segmentId;
        private final String content;

        public SegmentContentChangedEvent(
                DocumentSegmentEditorList source,
                boolean fromClient,
                @EventData("event.detail.id") String segmentId,
                @EventData("event.detail.content") String content) {
            super(source, fromClient);
            this.segmentId = segmentId == null ? "" : segmentId;
            this.content = content == null ? "" : content;
        }

        public String getSegmentId() {
            return segmentId;
        }

        public String getContent() {
            return content;
        }
    }

    @DomEvent("segment-delete-requested")
    public static final class SegmentDeleteRequestedEvent extends ComponentEvent<DocumentSegmentEditorList> {

        private final String segmentId;

        public SegmentDeleteRequestedEvent(
                DocumentSegmentEditorList source,
                boolean fromClient,
                @EventData("event.detail.id") String segmentId) {
            super(source, fromClient);
            this.segmentId = segmentId == null ? "" : segmentId;
        }

        public String getSegmentId() {
            return segmentId;
        }
    }
}
