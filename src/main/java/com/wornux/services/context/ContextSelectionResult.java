package com.wornux.services.context;

public sealed interface ContextSelectionResult permits ContextSelectionResult.NoAccess, ContextSelectionResult.Selected,
        ContextSelectionResult.SelectionRequired {
    record NoAccess() implements ContextSelectionResult {}

    record Selected(AvailableContextOption option) implements ContextSelectionResult {}

    record SelectionRequired(java.util.List<AvailableContextOption> options) implements ContextSelectionResult {}
}
