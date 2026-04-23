package com.wornux.documentingest.ui;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.wornux.documentingest.EditableSegmentVm;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

public class DocumentSegmentEditorList extends Composite<Div> {

  private final VerticalLayout list = new VerticalLayout();
  private BiConsumer<UUID, String> segmentChangeListener = (_, _) -> {};

  public DocumentSegmentEditorList() {
    list.setPadding(false);
    list.setSpacing(false);
    list.setMargin(false);
    list.addClassName("document-ingest-segment-list");

    var root = getContent();
    root.addClassName("document-ingest-segment-shell");
    root.add(list);
  }

  public void setSegmentChangeListener(BiConsumer<UUID, String> segmentChangeListener) {
    this.segmentChangeListener =
        segmentChangeListener == null ? (_, _) -> {} : segmentChangeListener;
  }

  public void setSegments(List<EditableSegmentVm> segments) {
    list.removeAll();
    for (EditableSegmentVm segment : segments) {
      list.add(createSegmentCard(segment));
    }
  }

  private Div createSegmentCard(EditableSegmentVm segment) {
    var ordinal = new Span("Segmento " + segment.ordinal());
    ordinal.addClassName("document-ingest-segment-ordinal");

    var heading =
        new Span(
            segment.headingPath() == null || segment.headingPath().isBlank()
                ? "Documento"
                : segment.headingPath());
    heading.addClassName("document-ingest-segment-heading");

    var meta =
        new Paragraph(
            "%d caracteres · %d tokens"
                .formatted(
                    segment.charCount() == null ? 0 : segment.charCount(),
                    segment.tokenCount() == null ? 0 : segment.tokenCount()));
    meta.addClassName("document-ingest-segment-meta");

    var area = new TextArea();
    area.setWidthFull();
    area.setValue(segment.content() == null ? "" : segment.content());
    area.setMinHeight("10rem");
    area.setMaxLength(8_000);
    area.setValueChangeMode(ValueChangeMode.EAGER);
    area.addClassName("document-ingest-segment-text");
    area.addValueChangeListener(
        event -> segmentChangeListener.accept(segment.id(), event.getValue()));

    var card = new Div(ordinal, heading, meta, area);
    card.addClassName("document-ingest-segment-card");
    return card;
  }
}
