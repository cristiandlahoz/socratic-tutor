package com.wornux.services.crunner;

import java.util.List;

public record CExamplePreparationResult(
    CExamplePreparationStatus status,
    String source,
    List<String> changes,
    String educationalNote,
    String risk) {

  public CExamplePreparationResult {
    status = status == null ? CExamplePreparationStatus.NOT_RUNNABLE : status;
    source = source == null ? "" : source;
    changes = changes == null ? List.of() : List.copyOf(changes);
    educationalNote = educationalNote == null ? "" : educationalNote;
    risk = risk == null ? "" : risk;
  }

  public static CExamplePreparationResult readyOriginal(String source) {
    return new CExamplePreparationResult(
        CExamplePreparationStatus.READY,
        source,
        List.of("Using the original snippet because preparation was unavailable."),
        "You can edit the code before rerunning it.",
        "preparation-unavailable");
  }

  public boolean ready() {
    return status == CExamplePreparationStatus.READY && !source.isBlank();
  }
}
