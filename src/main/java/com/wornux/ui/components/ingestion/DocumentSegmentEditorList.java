package com.wornux.ui.components.ingestion;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.wornux.ui.ingestion.EditableSegmentViewModel;
import com.wornux.ui.css.UiCss;

public class DocumentSegmentEditorList extends Composite<Div> {

    private final VerticalLayout list = new VerticalLayout();
    private BiConsumer<String, String> segmentChangeListener = (_, _) -> {};
    private Consumer<String> segmentDeleteListener = _ -> {};

    public DocumentSegmentEditorList() {
        list.setId("document-ingestion-segment-list");
        list.setPadding(false);
        list.setSpacing(false);
        list.setMargin(false);
        UiCss.DOCUMENT_INGEST_SEGMENT_LIST.addTo(list);

        var root = getContent();
        root.setId("document-ingestion-segment-editor-list");
        UiCss.DOCUMENT_INGEST_SEGMENT_SHELL.addTo(root);
        root.add(list);
    }

    public void setSegmentChangeListener(BiConsumer<String, String> segmentChangeListener) {
        this.segmentChangeListener = segmentChangeListener == null ? (_, _) -> {} : segmentChangeListener;
    }

    public void setSegmentDeleteListener(Consumer<String> segmentDeleteListener) {
        this.segmentDeleteListener = segmentDeleteListener == null ? _ -> {} : segmentDeleteListener;
    }

    public void setSegments(List<EditableSegmentViewModel> segments) {
        list.removeAll();
        for (EditableSegmentViewModel segment : segments) {
            list.add(createSegmentCard(segment));
        }
    }

    private Div createSegmentCard(EditableSegmentViewModel segment) {
        var ordinal = new Span("segmento %d".formatted(segment.ordinal()));
        UiCss.DOCUMENT_INGEST_SEGMENT_ORDINAL.addTo(ordinal);

        var deleteButton = new Button("Eliminar", new Icon(VaadinIcon.TRASH));
        deleteButton.setId("document-ingestion-segment-delete-%d".formatted(segment.ordinal()));
        deleteButton.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.TERTIARY);
        UiCss.DOCUMENT_INGEST_SEGMENT_DELETE_BUTTON.addTo(deleteButton);
        deleteButton.getElement().setAttribute("aria-label", "Eliminar segmento %d".formatted(segment.ordinal()));
        deleteButton.addClickListener(_ -> segmentDeleteListener.accept(segment.id()));

        var cardHeader = new HorizontalLayout(ordinal, deleteButton);
        UiCss.DOCUMENT_INGEST_SEGMENT_CARD_HEADER.addTo(cardHeader);
        cardHeader.setWidthFull();
        cardHeader.setPadding(false);
        cardHeader.setSpacing(true);

        var heading = new Span(
                segment.headingPath() == null || segment.headingPath().isBlank() ? "Documento" : segment.headingPath());
        UiCss.DOCUMENT_INGEST_SEGMENT_HEADING.addTo(heading);

        var meta = new Paragraph("%d caracteres · %d tokens%s".formatted(
            segment.charCount() == null ? 0 : segment.charCount(),
            segment.tokenCount() == null ? 0 : segment.tokenCount(),
            pageSummary(segment)));
        UiCss.DOCUMENT_INGEST_SEGMENT_META.addTo(meta);

        var provenance = new Div();
        UiCss.DOCUMENT_INGEST_SEGMENT_PROVENANCE.addTo(provenance);
        if (segment.captions() != null && !segment.captions().isEmpty()) {
            provenance.add(new Span("captions: %s".formatted(String.join(" · ", segment.captions()))));
        }
        if (segment.docItems() != null && !segment.docItems().isEmpty()) {
            provenance.add(new Span("refs: %s".formatted(String.join(" · ", segment.docItems()))));
        }
        provenance.setVisible(provenance.getComponentCount() > 0);

        var area = new TextArea();
        area.setId("document-ingestion-segment-text-%d".formatted(segment.ordinal()));
        area.getElement().setAttribute("data-segment-id", segment.id().toString());
        area.setWidthFull();
        area.setValue(segment.content() == null ? "" : segment.content());
        area.setMinHeight("10rem");
        area.setMaxLength(8_000);
        area.setValueChangeMode(ValueChangeMode.EAGER);
        UiCss.DOCUMENT_INGEST_SEGMENT_TEXT.addTo(area);
        area.addValueChangeListener(event -> segmentChangeListener.accept(segment.id(), event.getValue()));

        var card = new Div(cardHeader, heading, meta, provenance, area);
        card.setId("document-ingestion-segment-card-%d".formatted(segment.ordinal()));
        card.getElement().setAttribute("data-segment-id", segment.id().toString());
        UiCss.DOCUMENT_INGEST_SEGMENT_CARD.addTo(card);
        card.setWidthFull();
        return card;
    }

    private String pageSummary(EditableSegmentViewModel segment) {
        if (segment.pageNumbers() != null && !segment.pageNumbers().isEmpty()) {
            return " · paginas %s".formatted(segment.pageNumbers());
        }
        if (segment.pageNumber() != null) {
            return " · pagina %s".formatted(segment.pageNumber());
        }
        return "";
    }
}
