package com.wornux;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.router.PreserveOnRefresh;
import com.vaadin.flow.signals.Signal;
import com.wornux.chat.ChatUiController;
import com.wornux.chat.ChatUiState;
import com.wornux.chat.ConversationSummary;
import com.wornux.chat.ui.WidthAwareLabel;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Layout
@PreserveOnRefresh
public class MainLayout extends AppLayout {

    private static final Locale SPANISH_LOCALE = Locale.of("es", "DO");
    private static final DateTimeFormatter CONVERSATION_DAY_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy", SPANISH_LOCALE);

    private final Button newChatButton;
    private final Div timelineRoot;
    private final Div timelineRows;
    private final Div emptyHistory;
    private final Span historyCount;

    private sealed interface TimelineEntry permits TimelineDividerEntry, TimelineThreadEntry {

        String label();

        String nodeId();

        String lane();
    }

    private record TimelineDividerEntry(String label, String nodeId) implements TimelineEntry {

        @Override
        public String lane() {
            return "divider";
        }
    }

    private record TimelineThreadEntry(ConversationSummary conversation, String nodeId) implements TimelineEntry {
        @Override
        public String label() {
            return conversation.title();
        }

        @Override
        public String lane() {
            return "thread";
        }
    }

    public MainLayout(ChatUiState state, ChatUiController controller) {
        setPrimarySection(Section.DRAWER);

        var drawerContent = new Div();
        drawerContent.addClassNames("shell-drawer-content", "chat-sidebar-shell");
        drawerContent.setSizeFull();

        var appTitle = new H1("Tutor Crow");
        appTitle.addClassName("chat-sidebar-app-title");

        var appDescription = new Paragraph("Tutor para explorar ideas, resolver dudas y aprender con preguntas guiadas introducción a la algoritmia.");
        appDescription.addClassName("chat-sidebar-app-description");

        var appHeader = new Div(appTitle, appDescription);
        appHeader.addClassName("chat-sidebar-app-header");

        newChatButton = createActionButton(VaadinIcon.PLUS, "Nuevo chat");
        newChatButton.addClickListener(_ -> controller.startNewChat());

        var helpButton = createActionButton(VaadinIcon.INFO_CIRCLE_O, "Como funciona");

        var infoPopover = new Popover();
        infoPopover.setTarget(helpButton);
        infoPopover.setModal(false);
        infoPopover.addClassName("chat-sidebar-help-popover");

        var infoTitle = new Span("Como funciona");
        infoTitle.addClassName("chat-sidebar-help-title");

        var infoCopy = new Paragraph(
                "Retoma conversaciones previas, abre un nuevo hilo y navega el historial como una linea de tiempo viva.");
        infoCopy.addClassName("chat-sidebar-help-description");
        infoPopover.add(new Div(infoTitle, infoCopy));

        var actionsRow = new Div(newChatButton, helpButton);
        actionsRow.addClassName("chat-sidebar-actions");

        var historyTitle = new H1("Historial");
        historyTitle.addClassName("chat-sidebar-panel-title");

        historyCount = new Span();
        historyCount.addClassName("chat-sidebar-panel-count");

        var historyTitleRow = new Div(historyTitle, historyCount);
        historyTitleRow.addClassName("chat-sidebar-panel-title-row");

        var historyDescription = new Paragraph("Hilos recientes ordenados por fecha y conectados como una sola ruta.");
        historyDescription.addClassName("chat-sidebar-panel-description");

        var historyHeader = new Div(historyTitleRow, historyDescription);
        historyHeader.addClassName("chat-sidebar-header");

        var emptyTitle = new Span("Sin conversaciones todavía");
        emptyTitle.addClassName("chat-sidebar-empty-title");

        var emptyDescription = new Paragraph("Inicia un nuevo chat para empezar a construir tu historial del tutor.");
        emptyDescription.addClassName("chat-sidebar-empty-description");

        emptyHistory = new Div(emptyTitle, emptyDescription);
        emptyHistory.addClassName("chat-sidebar-empty");

        timelineRows = new Div();
        timelineRows.addClassName("chat-sidebar-timeline-rows");

        timelineRoot = new Div(timelineRows);
        timelineRoot.addClassName("chat-sidebar-timeline");

        var historyBody = new Div(emptyHistory, timelineRoot);
        historyBody.addClassName("chat-sidebar-history-body");

        var historySection = new Div(historyHeader, historyBody);
        historySection.addClassName("chat-sidebar-history-section");

        drawerContent.add(appHeader, actionsRow, historySection);

        var drawerScroller = new Scroller(drawerContent, Scroller.ScrollDirection.NONE);
        drawerScroller.setSizeFull();
        drawerScroller.addClassName("shell-drawer-scroller");
        addToDrawer(drawerScroller);

        bindConversationState(state, controller);
        installTimelineGraphRenderer();
    }

    private Button createActionButton(VaadinIcon icon, String label) {
        var iconComponent = new Icon(icon);
        iconComponent.addClassName("chat-sidebar-action-icon");

        var button = new Button(label);
        button.addClassName("chat-sidebar-action-button");
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.setIcon(iconComponent);
        button.getThemeNames().remove("icon");
        button.setWidthFull();
        button.setAriaLabel(label);
        return button;
    }

    private void bindConversationState(ChatUiState state, ChatUiController controller) {
        newChatButton.bindEnabled(Signal.not(state.responseInProgress().asReadonly()));
        Signal.effect(timelineRoot, () -> renderConversationTimeline(
                state.conversationHistory().get().stream().map(Signal::get).toList(),
                state.activeConversationId().get(),
                state.responseInProgress().get(),
                controller
        ));
    }

    private void renderConversationTimeline(List<ConversationSummary> conversations,
                                            UUID activeConversationId,
                                            boolean disabled,
                                            ChatUiController controller) {
        historyCount.setText(formatConversationCount(conversations.size()));
        emptyHistory.setVisible(conversations.isEmpty());
        timelineRoot.setVisible(!conversations.isEmpty());
        timelineRows.removeAll();

        if (conversations.isEmpty()) {
            redrawTimelineGraph();
            return;
        }

        buildTimelineEntries(conversations).stream()
                .map(entry -> createTimelineEntry(entry, activeConversationId, disabled, controller))
                .forEach(timelineRows::add);
        redrawTimelineGraph();
    }

    private Component createTimelineEntry(TimelineEntry entry,
                                          UUID activeConversationId,
                                          boolean disabled,
                                          ChatUiController controller) {
        return switch (entry) {
            case TimelineDividerEntry(String _, String _) -> createDividerEntry((TimelineDividerEntry) entry);
            case TimelineThreadEntry(_, _) ->
                    createThreadEntry((TimelineThreadEntry) entry, activeConversationId, disabled, controller);
        };
    }

    private Component createDividerEntry(TimelineDividerEntry entry) {
        var node = createNode(entry.nodeId(), entry.lane(), false);

        var label = new Span(entry.label());
        label.addClassName("chat-sidebar-divider-label");

        var row = new Div(node, label);
        row.addClassNames("chat-sidebar-entry-row", "chat-sidebar-divider-row");
        return row;
    }

    private Component createThreadEntry(TimelineThreadEntry entry,
                                        UUID activeConversationId,
                                        boolean disabled,
                                        ChatUiController controller) {
        var conversation = entry.conversation();
        var active = conversation.id().equals(activeConversationId);

        var node = createNode(entry.nodeId(), entry.lane(), active);

        var title = new WidthAwareLabel(conversation.title());
        title.setSafetyPixels(14);
        title.addClassName("chat-sidebar-item-title");

        var content = new Div(node, title);
        content.addClassNames("chat-sidebar-entry-row", "chat-sidebar-thread-row");
        if (active) {
            content.addClassName("is-active");
        }

        var button = new Button(content);
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.addClassName("chat-sidebar-item-button");
        button.getThemeNames().remove("icon");
        button.setWidthFull();
        button.setEnabled(!disabled);
        button.getElement().setAttribute("title", conversation.title());
        button.getElement().setAttribute("aria-label", conversation.title());
        if (active) {
            button.getElement().setAttribute("aria-current", "page");
        }
        button.addClickListener(_ -> controller.openConversation(conversation.id()));
        return button;
    }

    private Div createNode(String nodeId, String lane, boolean active) {
        var node = new Div();
        node.addClassName("chat-sidebar-node");
        node.getElement().setAttribute("data-node-id", nodeId);
        node.getElement().setAttribute("data-lane", lane);
        if (active) {
            node.addClassName("is-active");
        }
        return node;
    }

    private List<TimelineEntry> buildTimelineEntries(List<ConversationSummary> conversations) {
        var groupedConversations = new LinkedHashMap<LocalDate, List<ConversationSummary>>();
        for (var conversation : conversations) {
            groupedConversations.computeIfAbsent(toConversationDay(conversation), _ -> new ArrayList<>()).add(conversation);
        }

        var entries = new ArrayList<TimelineEntry>();
        int index = 0;
        for (var groupedEntry : groupedConversations.entrySet()) {
            entries.add(new TimelineDividerEntry(formatDayLabel(groupedEntry.getKey()), "divider-" + index++));
            for (var conversation : groupedEntry.getValue()) {
                entries.add(new TimelineThreadEntry(conversation, "thread-" + conversation.id()));
            }
        }
        return entries;
    }

    private void installTimelineGraphRenderer() {
        timelineRoot.getElement().executeJs("""
                const root = this;
                if (root.__timelineGraphCleanup) {
                  root.__timelineGraphCleanup();
                }

                const SVG_NS = 'http://www.w3.org/2000/svg';

                const createSvgLayer = (className) => {
                  const layer = document.createElementNS(SVG_NS, 'svg');
                  layer.classList.add(className);
                  layer.setAttribute('aria-hidden', 'true');
                  root.prepend(layer);
                  return layer;
                };

                const ensureLayers = () => {
                  if (!root.__timelineGraphStaticSvg?.isConnected) {
                    root.__timelineGraphStaticSvg = createSvgLayer('chat-sidebar-timeline-edges');
                  }
                  if (!root.__timelineGraphFxSvg?.isConnected) {
                    root.__timelineGraphFxSvg = createSvgLayer('chat-sidebar-timeline-effects');
                  }
                  return {
                    staticSvg: root.__timelineGraphStaticSvg,
                    fxSvg: root.__timelineGraphFxSvg,
                  };
                };

                const buildPoint = (node, rootRect) => {
                  const rect = node.getBoundingClientRect();
                  return {
                    node,
                    id: node.dataset.nodeId,
                    x: rect.left - rootRect.left + rect.width / 2,
                    y: rect.top - rootRect.top + rect.height / 2 + root.scrollTop,
                  };
                };

                const appendSegment = (pathParts, fromPoint, toPoint) => {
                  if (Math.abs(fromPoint.x - toPoint.x) < 1) {
                    pathParts.push(`L ${toPoint.x} ${toPoint.y}`);
                    return;
                  }

                  const midY = (fromPoint.y + toPoint.y) / 2;
                  pathParts.push(`C ${fromPoint.x} ${midY}, ${toPoint.x} ${midY}, ${toPoint.x} ${toPoint.y}`);
                };

                const animateTraveler = (points, fromId, toId) => {
                  const { fxSvg } = ensureLayers();
                  const startIndex = points.findIndex((point) => point.id === fromId);
                  const endIndex = points.findIndex((point) => point.id === toId);
                  if (startIndex < 0 || endIndex < 0 || startIndex === endIndex) {
                    return;
                  }

                  const routePoints = startIndex < endIndex
                    ? points.slice(startIndex, endIndex + 1)
                    : points.slice(endIndex, startIndex + 1).reverse();

                  const routePath = document.createElementNS(SVG_NS, 'path');
                  routePath.classList.add('chat-sidebar-travel-path');
                  const pathParts = [`M ${routePoints[0].x} ${routePoints[0].y}`];
                  for (let i = 0; i < routePoints.length - 1; i += 1) {
                    appendSegment(pathParts, routePoints[i], routePoints[i + 1]);
                  }
                  routePath.setAttribute('d', pathParts.join(' '));
                  fxSvg.replaceChildren();
                  fxSvg.append(routePath);

                  const glow = document.createElementNS(SVG_NS, 'circle');
                  glow.classList.add('chat-sidebar-traveler-glow');
                  glow.setAttribute('r', '12');
                  glow.setAttribute('opacity', '0.7');

                  const core = document.createElementNS(SVG_NS, 'circle');
                  core.classList.add('chat-sidebar-traveler-core');
                  core.setAttribute('r', '5.2');

                  const totalLength = routePath.getTotalLength();
                  const duration = 420;
                  const startedAt = performance.now();
                  const startPoint = routePath.getPointAtLength(0);

                  glow.setAttribute('cx', startPoint.x);
                  glow.setAttribute('cy', startPoint.y);
                  core.setAttribute('cx', startPoint.x);
                  core.setAttribute('cy', startPoint.y);

                  fxSvg.append(glow, core);

                  if (root.__timelineGraphAnimationFrame) {
                    cancelAnimationFrame(root.__timelineGraphAnimationFrame);
                  }

                  const move = (now) => {
                    const progress = Math.min(1, (now - startedAt) / duration);
                    const eased = 1 - Math.pow(1 - progress, 3);
                    const point = routePath.getPointAtLength(totalLength * eased);
                    glow.setAttribute('cx', point.x);
                    glow.setAttribute('cy', point.y);
                    glow.setAttribute('opacity', String(0.7 * (1 - progress * 0.28)));
                    core.setAttribute('cx', point.x);
                    core.setAttribute('cy', point.y);

                    if (progress < 1) {
                      root.__timelineGraphAnimationFrame = requestAnimationFrame(move);
                    } else {
                      fxSvg.replaceChildren();
                      root.__timelineGraphAnimationFrame = null;
                    }
                  };

                  root.__timelineGraphAnimationFrame = requestAnimationFrame(move);
                };

                const drawGraph = () => {
                  const nodes = [...root.querySelectorAll('.chat-sidebar-node')];
                  const rootRect = root.getBoundingClientRect();
                  const { staticSvg, fxSvg } = ensureLayers();
                  const width = Math.max(1, root.clientWidth);
                  const height = Math.max(1, root.scrollHeight);
                  [staticSvg, fxSvg].forEach((layer) => {
                    layer.setAttribute('viewBox', `0 0 ${width} ${height}`);
                    layer.setAttribute('width', `${width}`);
                    layer.setAttribute('height', `${height}`);
                  });
                  staticSvg.replaceChildren();

                  if (nodes.length < 2) {
                    return;
                  }

                  const points = nodes.map((node) => buildPoint(node, rootRect));

                  for (let i = 0; i < points.length - 1; i += 1) {
                    const fromPoint = points[i];
                    const toPoint = points[i + 1];

                    const path = document.createElementNS(SVG_NS, 'path');
                    path.classList.add('chat-sidebar-edge');
                    if (fromPoint.node.classList.contains('is-active') || toPoint.node.classList.contains('is-active')) {
                      path.classList.add('touches-active');
                    }

                    const pathParts = [`M ${fromPoint.x} ${fromPoint.y}`];
                    appendSegment(pathParts, fromPoint, toPoint);
                    path.setAttribute('d', pathParts.join(' '));
                    staticSvg.append(path);
                  }

                  const currentActiveNode = nodes.find((node) => node.classList.contains('is-active'));
                  const currentActiveId = currentActiveNode?.dataset.nodeId ?? null;
                  const previousActiveId = root.__timelineGraphActiveId ?? null;

                  if (previousActiveId && currentActiveId && previousActiveId !== currentActiveId) {
                    animateTraveler(points, previousActiveId, currentActiveId);
                  }

                  root.__timelineGraphActiveId = currentActiveId;
                };

                const resizeObserver = new ResizeObserver(() => drawGraph());
                resizeObserver.observe(root);
                const rows = root.querySelector('.chat-sidebar-timeline-rows');
                if (rows) {
                  resizeObserver.observe(rows);
                }

                const handleScroll = () => drawGraph();
                root.addEventListener('scroll', handleScroll, { passive: true });

                root.__timelineGraphDraw = drawGraph;
                root.__timelineGraphCleanup = () => {
                  resizeObserver.disconnect();
                  if (root.__timelineGraphAnimationFrame) {
                    cancelAnimationFrame(root.__timelineGraphAnimationFrame);
                  }
                  root.removeEventListener('scroll', handleScroll);
                  root.__timelineGraphStaticSvg?.remove();
                  root.__timelineGraphFxSvg?.remove();
                  delete root.__timelineGraphActiveId;
                  delete root.__timelineGraphAnimationFrame;
                  delete root.__timelineGraphStaticSvg;
                  delete root.__timelineGraphFxSvg;
                  delete root.__timelineGraphDraw;
                  delete root.__timelineGraphCleanup;
                };
                requestAnimationFrame(drawGraph);
                """);
    }

    private void redrawTimelineGraph() {
        timelineRoot.getElement().executeJs("requestAnimationFrame(() => { if (!this.__timelineGraphDraw) { this.dispatchEvent(new Event('timeline-graph-missing')); } this.__timelineGraphDraw?.(); });");
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        timelineRoot.getElement().executeJs("""
                if (!this.__timelineGraphDraw) {
                  const event = new Event('timeline-graph-missing');
                  this.dispatchEvent(event);
                }
                requestAnimationFrame(() => this.__timelineGraphDraw?.());
                """);
        installTimelineGraphRenderer();
    }

    private LocalDate toConversationDay(ConversationSummary conversation) {
        return conversation.updatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private String formatDayLabel(LocalDate day) {
        var today = LocalDate.now(ZoneId.systemDefault());
        if (day.equals(today)) {
            return "Hoy";
        }
        if (day.equals(today.minusDays(1))) {
            return "Ayer";
        }
        return CONVERSATION_DAY_FORMATTER.format(day);
    }

    private String formatConversationCount(int count) {
        return "%d hilos".formatted(count);
    }
}
