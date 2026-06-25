package com.wornux.usecases.uc002;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class UC002ConsistentCodingRulesTest {

    private static final Path PROJECT_ROOT = Path.of("");
    private static final Path CONVERSATION_UI = PROJECT_ROOT.resolve("src/main/java/com/wornux/ui/conversation");
    private static final Path RESOURCES = PROJECT_ROOT.resolve("src/main/resources/META-INF/resources");

    @Test
    void mainFlow_conversationUiUsesCurrentDomainNameAndTypedCssUtility() throws IOException {
        assertThat(CONVERSATION_UI.resolve("ConversationView.java")).exists();
        assertThat(CONVERSATION_UI.resolve("ConversationViewModel.java")).exists();
        assertThat(CONVERSATION_UI.resolve("ConversationState.java")).exists();
        assertThat(CONVERSATION_UI.resolve("ConversationCss.java")).exists();
        assertThat(CONVERSATION_UI.resolve("CssClass.java")).exists();
        assertThat(PROJECT_ROOT.resolve("src/main/java/com/wornux/ui/chat")).doesNotExist();

        var conversationView = Files.readString(CONVERSATION_UI.resolve("ConversationView.java"));

        assertThat(conversationView).contains("@Route(value = \"chat\"");
        assertThat(conversationView).contains("ConversationCss.VIEW.addTo(root)");
        assertThat(conversationView).contains("ConversationCss.COMPOSER_INPUT.addTo(composerField)");
        assertThat(conversationView).doesNotContain("ChatView");
    }

    @Test
    void af1_cleanupKeepsScopeLimitedToConversationSurface() throws IOException {
        var changedSurfaceMarkers = readAll(List.of(
            PROJECT_ROOT.resolve("src/main/java/com/wornux/ui/conversation"),
            PROJECT_ROOT.resolve("src/main/java/com/wornux/ui/components/chat/StudentQuestionPanel.java"),
            RESOURCES.resolve("styles/conversation-thread.css"),
            RESOURCES.resolve("styles/conversation-composer.css"),
            RESOURCES.resolve("styles/conversation-empty.css")));

        assertThat(changedSurfaceMarkers).contains("conversation-view", "conversation-question", "conversation-thread");
    }

    @Test
    void af2_dynamicMessageClassReferencesAreRenamedTogether() throws IOException {
        var javaView = Files.readString(CONVERSATION_UI.resolve("ConversationView.java"));
        var codeMessageList = Files.readString(RESOURCES.resolve("frontend/code-message-list.ts"));
        var codeMessageBody = Files.readString(RESOURCES.resolve("frontend/code-message-body.ts"));

        assertThat(javaView).contains("ConversationCss.MESSAGE_ASSISTANT");
        assertThat(codeMessageList).contains("conversation-message--assistant");
        assertThat(codeMessageBody).contains("conversation-message--user");
    }

    @Test
    void af3_routeCompatibilityIsPreservedWhileInternalNamesChange() throws IOException {
        var view = Files.readString(CONVERSATION_UI.resolve("ConversationView.java"));
        var navigation = Files.readString(CONVERSATION_UI.resolve("ConversationNavigationOrchestrator.java"));

        assertThat(view).contains("@Route(value = \"chat\"");
        assertThat(navigation).contains("new Location(\"chat\"");
        assertThat(view).contains("ConversationView.class");
    }

    @Test
    void af4_compileAndTestsCoverCleanupCausedBreakage() {
        assertThat(PROJECT_ROOT.resolve("src/test/java/com/wornux/ui/conversation/RouteAccessViewTest.java")).exists();
        assertThat(PROJECT_ROOT.resolve("src/test/java/com/wornux/ui/chat/RouteAccessViewTest.java")).doesNotExist();
    }

    @Test
    void af5_reviewerCanTraceIntentFromNames() throws IOException {
        var viewModel = Files.readString(CONVERSATION_UI.resolve("ConversationViewModel.java"));
        var mainLayout = Files.readString(PROJECT_ROOT.resolve("src/main/java/com/wornux/ui/MainLayout.java"));

        assertThat(viewModel).contains("onStartNewConversation");
        assertThat(mainLayout).contains("newConversationButton");
        assertThat(mainLayout).doesNotContain("newChatButton");
    }

    @Test
    void br01_cleanedConversationCssUsesBemClasses() throws IOException {
        var css = readAll(List.of(
            RESOURCES.resolve("styles/conversation-thread.css"),
            RESOURCES.resolve("styles/conversation-composer.css"),
            RESOURCES.resolve("styles/conversation-empty.css"),
            RESOURCES.resolve("styles/shell.css"),
            RESOURCES.resolve("styles/c-runner.css")));

        assertThat(css).contains(".conversation-view__pane");
        assertThat(css).contains(".conversation-composer__input");
        assertThat(css).contains(".conversation-question__option-description--inline");
        assertThat(css).contains(".conversation-message--assistant");
        assertThat(css).doesNotContain(".chat-composer-input", ".chat-empty", ".chat-thread");
    }

    @Test
    void br02_deadLegacyConversationCssClassesAreRemovedFromCleanedSurface() throws IOException {
        var cleanedCss = readAll(List.of(
            RESOURCES.resolve("styles/conversation-thread.css"),
            RESOURCES.resolve("styles/conversation-composer.css"),
            RESOURCES.resolve("styles/conversation-empty.css"),
            RESOURCES.resolve("styles/shell.css"),
            RESOURCES.resolve("styles/c-runner.css")));

        assertThat(cleanedCss).doesNotContain(".chat-view", ".chat-pane", ".chat-scroll-region", ".chat-debug-split");
    }

    @Test
    void br03_unnecessaryRadiusAliasChainsAreFlattened() throws IOException {
        var tokens = Files.readString(RESOURCES.resolve("styles/tokens.css"));

        assertThat(tokens).contains("--chat-panel-radius: 10px;");
        assertThat(tokens).contains("--chat-panel-radius-tight: 6px;");
        assertThat(tokens).contains("--chat-message-radius: 8px;");
        assertThat(tokens).doesNotContain("--chat-panel-radius: var(--chat-radius-compact-l)");
    }

    @Test
    void br04_javaConversationUiUsesTypedCssConstants() throws IOException {
        var view = Files.readString(CONVERSATION_UI.resolve("ConversationView.java"));
        var questionPanel = Files.readString(PROJECT_ROOT.resolve("src/main/java/com/wornux/ui/components/chat/StudentQuestionPanel.java"));

        assertThat(view).contains("ConversationCss.");
        assertThat(questionPanel).contains("ConversationCss.");
        assertThat(view).doesNotContain("addClassName(\"conversation-");
    }

    @Test
    void br05_obsoleteChatUiDomainNamesAreNotUsedForConversationUi() throws IOException {
        var conversationSources = readAllJava(CONVERSATION_UI);

        assertThat(conversationSources).doesNotContain("class ChatView", "class ChatState", "class ChatViewModel");
        assertThat(conversationSources).contains("class ConversationView", "class ConversationState", "class ConversationViewModel");
    }

    @Test
    void br06_methodsHaveIntentRevealingConversationNames() throws IOException {
        var viewModel = Files.readString(CONVERSATION_UI.resolve("ConversationViewModel.java"));
        var controller = Files.readString(PROJECT_ROOT.resolve("src/main/java/com/wornux/ui/ingestion/DocumentIngestionUiController.java"));

        assertThat(viewModel).contains("onOpenConversation", "onStartNewConversation", "refreshConversationHistory");
        assertThat(controller).contains("returnToConversation");
    }

    @Test
    void br07_vaadinFlowRouteTypesReferenceRenamedConversationView() throws IOException {
        var sources = readAll(List.of(
            PROJECT_ROOT.resolve("src/main/java/com/wornux/ui/professor/ProfessorWorkspaceView.java"),
            PROJECT_ROOT.resolve("src/main/java/com/wornux/ui/student/StudentWorkspaceView.java"),
            PROJECT_ROOT.resolve("src/main/java/com/wornux/ui/ingestion/DocumentIngestionUiController.java")));

        assertThat(sources).contains("ConversationView.class");
        assertThat(sources).doesNotContain("ChatView.class");
    }

    @Test
    void br08_cleanupDoesNotIntroduceApplicationRoutesBeyondCompatibilityRoute() throws IOException {
        var view = Files.readString(CONVERSATION_UI.resolve("ConversationView.java"));
        var routePattern = Pattern.compile("@Route\\(value = \\\"([^\\\"]+)\\\"");
        var matcher = routePattern.matcher(view);

        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(1)).isEqualTo("chat");
    }

    private static String readAllJava(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .map(UC002ConsistentCodingRulesTest::readUnchecked)
                    .reduce("", String::concat);
        }
    }

    private static String readAll(List<Path> paths) throws IOException {
        var builder = new StringBuilder();
        for (Path path : paths) {
            if (Files.isDirectory(path)) {
                try (Stream<Path> files = Files.walk(path)) {
                    files.filter(Files::isRegularFile).map(UC002ConsistentCodingRulesTest::readUnchecked).forEach(builder::append);
                }
            }
            else {
                builder.append(Files.readString(path));
            }
        }
        return builder.toString();
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        }
        catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
