package com.wornux.ui.conversation;

public final class ConversationCss {

    public static final CssClass VIEW = css("conversation-view");
    public static final CssClass PANE = css("conversation-view__pane");
    public static final CssClass SCROLL_REGION = css("conversation-view__scroll-region");
    public static final CssClass DEBUG_SPLIT = css("conversation-view__debug-split");
    public static final CssClass DEBUG_SPLIT_COLLAPSED = css("conversation-view__debug-split--collapsed");
    public static final CssClass DEBUGGER_TOGGLE = css("conversation-view__debugger-toggle");

    public static final CssClass THREAD = css("conversation-thread");
    public static final CssClass MESSAGE_USER = css("conversation-message--user");
    public static final CssClass MESSAGE_ASSISTANT = css("conversation-message--assistant");
    public static final CssClass MESSAGE_LOADING = css("is-loading");

    public static final CssClass EMPTY = css("conversation-empty");
    public static final CssClass EMPTY_LAYOUT = css("conversation-empty__layout");
    public static final CssClass EMPTY_CONTENT = css("conversation-empty__content");
    public static final CssClass EMPTY_FRAME = css("conversation-empty__frame");
    public static final CssClass EMPTY_ILLUSTRATION = css("conversation-empty__illustration");
    public static final CssClass EMPTY_TITLE = css("conversation-empty__title");
    public static final CssClass EMPTY_DESCRIPTION = css("conversation-empty__description");

    public static final CssClass COMPOSER = css("conversation-composer");
    public static final CssClass COMPOSER_FIELD_WRAP = css("conversation-composer__field-wrap");
    public static final CssClass COMPOSER_INPUT = css("conversation-composer__input");
    public static final CssClass COMPOSER_SEND_BUTTON = css("conversation-composer__send-button");

    public static final CssClass USAGE = css("conversation-usage");
    public static final CssClass USAGE_COPY = css("conversation-usage__copy");
    public static final CssClass USAGE_TEXT = css("conversation-usage__text");
    public static final CssClass USAGE_LINEAGE = css("conversation-usage__lineage");
    public static final CssClass USAGE_HELP_BUTTON = css("conversation-usage__help-button");

    public static final CssClass QUESTION = css("conversation-question");
    public static final CssClass QUESTION_OPEN = css("conversation-question--open");
    public static final CssClass QUESTION_SUBMITTING = css("conversation-question--submitting");
    public static final CssClass QUESTION_HEADER_ROW = css("conversation-question__header-row");
    public static final CssClass QUESTION_TITLE = css("conversation-question__title");
    public static final CssClass QUESTION_PROGRESS = css("conversation-question__progress");
    public static final CssClass QUESTION_VIEWPORT = css("conversation-question__viewport");
    public static final CssClass QUESTION_CARD = css("conversation-question__card");
    public static final CssClass QUESTION_HEADER = css("conversation-question__header");
    public static final CssClass QUESTION_PROMPT = css("conversation-question__prompt");
    public static final CssClass QUESTION_OPTIONS = css("conversation-question__options");
    public static final CssClass QUESTION_OPTION_ROW = css("conversation-question__option-row");
    public static final CssClass QUESTION_OPTION = css("conversation-question__option");
    public static final CssClass QUESTION_OPTION_SELECTED = css("is-selected");
    public static final CssClass QUESTION_OPTION_COPY = css("conversation-question__option-copy");
    public static final CssClass QUESTION_OPTION_LABEL = css("conversation-question__option-label");
    public static final CssClass QUESTION_OPTION_INFO = css("conversation-question__option-info");
    public static final CssClass QUESTION_OPTION_POPOVER = css("conversation-question__option-popover");
    public static final CssClass QUESTION_OPTION_DESCRIPTION = css("conversation-question__option-description");
    public static final CssClass QUESTION_OPTION_DESCRIPTION_INLINE = css("conversation-question__option-description--inline");
    public static final CssClass QUESTION_OPTION_DESCRIPTION_POPOVER = css("conversation-question__option-description--popover");
    public static final CssClass QUESTION_OPTION_MOBILE_HEADER = css("conversation-question__option-mobile-header");
    public static final CssClass QUESTION_CUSTOM_TEXT = css("conversation-question__custom-text");
    public static final CssClass QUESTION_COMPOSER = css("conversation-question__composer");
    public static final CssClass QUESTION_COMPOSER_WRAP = css("conversation-question__composer-wrap");
    public static final CssClass QUESTION_COMPOSER_ACTIONS = css("conversation-question__composer-actions");
    public static final CssClass QUESTION_NAV_BUTTON = css("conversation-question__nav-button");
    public static final CssClass QUESTION_SUBMIT_BUTTON = css("conversation-question__submit-button");

    private ConversationCss() {
    }

    private static CssClass css(String value) {
        return new CssClass(value);
    }
}
