package com.wornux.ui.components.ingestion;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.progressbar.ProgressBar;

public class DocumentStatusPanel extends Composite<Div> {

    private final Span badge = new Span("Listo");
    private final Span title = new Span("Sin documento cargado");
    private final Paragraph message = new Paragraph("Arrastra un PDF para empezar el flujo ETL.");
    private final Paragraph failure = new Paragraph();
    private final ProgressBar progressBar = new ProgressBar();

    public DocumentStatusPanel() {
        badge.addClassName("document-ingest-status-badge");
        title.addClassName("document-ingest-status-title");
        message.addClassName("document-ingest-status-message");
        failure.addClassName("document-ingest-status-failure");
        failure.setVisible(false);

        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.addClassName("document-ingest-status-progress");

        var root = getContent();
        root.addClassName("document-ingest-status-panel");
        root.add(badge, title, message, progressBar, failure);
    }

    public void setStatus(String fileName, String stageLabel, boolean busy, boolean indexed, String failureMessage) {
        title.setText(fileName == null || fileName.isBlank() ? "Sin documento cargado" : fileName);
        message.setText(stageLabel == null || stageLabel.isBlank() ? "Sube un PDF para comenzar." : stageLabel);
        progressBar.setVisible(busy);
        failure.setVisible(failureMessage != null && !failureMessage.isBlank());
        failure.setText(failureMessage == null ? "" : failureMessage);

        if (indexed) {
            badge.setText("Indexado");
        }
        else if (busy) {
            badge.setText("Procesando");
        }
        else if (failureMessage != null && !failureMessage.isBlank()) {
            badge.setText("Error");
        }
        else {
            badge.setText("Revision");
        }
    }
}
