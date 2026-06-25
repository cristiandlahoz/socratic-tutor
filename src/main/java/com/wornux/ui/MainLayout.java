package com.wornux.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.router.PreserveOnRefresh;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.spring.annotation.RouteScopeOwner;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.enums.ThemePreference;
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.components.ProfileDrawerCard;
import com.wornux.ui.components.ToggleIcon;
import com.wornux.ui.components.sidebar.ChatDrawerActions;
import com.wornux.ui.components.sidebar.ConversationHistoryDrawer;
import com.wornux.ui.components.sidebar.WorkspaceDrawerNavigation;
import com.wornux.ui.conversation.ConversationState;
import com.wornux.ui.conversation.ConversationViewModel;
import com.wornux.ui.layout.MainLayoutAccess;
import jakarta.annotation.security.PermitAll;

@Layout
@PreserveOnRefresh
@PermitAll
public class MainLayout extends AppLayout {

    private final ConversationViewModel viewModel;

    public MainLayout(
            @RouteScopeOwner(MainLayout.class) ConversationState state,
            @RouteScopeOwner(MainLayout.class) ConversationViewModel viewModel,
            AuthenticatedAccountService authenticatedAccountService,
            WorkspaceRoutingService workspaceRoutingService,
            AuthenticationContext authenticationContext) {
        setPrimarySection(Section.DRAWER);
        this.viewModel = viewModel;
        this.viewModel.initializeShellState();

        var drawerContent = new Div();
        drawerContent.addClassNames("shell-drawer-content", "chat-sidebar-shell", "sidebar-rail-shell");
        drawerContent.setSizeFull();

        drawerContent.add(createRailEntry("brand", createBrandSection()));

        var currentAccount = authenticatedAccountService.currentAccount();
        var access = currentAccount
                .map(account -> resolveAccess(account, workspaceRoutingService))
                .orElseGet(MainLayoutAccess::none);

        if (currentAccount.isPresent()) {
            drawerContent.add(createRailEntry("actions", new WorkspaceDrawerNavigation(access)));
            if (access.canChat()) {
                drawerContent.add(createRailEntry("chat", new ChatDrawerActions(access, state, this.viewModel)));
                drawerContent.add(new ConversationHistoryDrawer(state, this.viewModel));
            }
            currentAccount.map(account -> new ProfileDrawerCard(
                account,
                createThemePreferenceControl(state, this.viewModel),
                authenticationContext::logout))
                    .map(profileCard -> createRailEntry("profile", profileCard))
                    .ifPresent(drawerContent::add);
        }

        var drawerScroller = new Scroller(drawerContent, Scroller.ScrollDirection.NONE);
        drawerScroller.setSizeFull();
        drawerScroller.addClassName("shell-drawer-scroller");
        addToDrawer(drawerScroller);
    }

    private MainLayoutAccess resolveAccess(Account account, WorkspaceRoutingService workspaceRoutingService) {
        return new MainLayoutAccess(
            workspaceRoutingService.canAccessWorkspace(account, WorkspaceDestination.SYSTEM_ADMIN),
            workspaceRoutingService.canAccessWorkspace(account, WorkspaceDestination.TENANT_ADMIN),
            workspaceRoutingService.canAccessWorkspace(account, WorkspaceDestination.PROFESSOR),
            workspaceRoutingService.canAccessWorkspace(account, WorkspaceDestination.STUDENT));
    }

    private Div createBrandSection() {
        var appTitle = new H1("Tutor Socrático");
        appTitle.addClassName("chat-sidebar-app-title");

        var drawerToggle = createDrawerToggle("shell-drawer-toggle-inside", "Cerrar menu");

        var appTitleRow = new Div(appTitle, drawerToggle);
        appTitleRow.addClassName("chat-sidebar-app-title-row");

        var appDescription = new Paragraph(
                "Tutor para explorar ideas, resolver dudas y aprender introducción a la algoritmia con conversaciones guiadas.");
        appDescription.addClassName("chat-sidebar-app-description");

        var appHeader = new Div(appTitleRow, appDescription);
        appHeader.addClassName("chat-sidebar-app-header");
        return appHeader;
    }

    private DrawerToggle createDrawerToggle(String className, String ariaLabel) {
        var toggle = new DrawerToggle();
        toggle.setIcon(new ToggleIcon());
        toggle.addThemeVariants(ButtonVariant.TERTIARY);
        toggle.addClassName(className);
        toggle.setAriaLabel(ariaLabel);
        return toggle;
    }

    private Div createThemePreferenceControl(ConversationState state, ConversationViewModel viewModel) {
        var label = new Span("Tema");
        label.addClassName("chat-sidebar-theme-label");

        var options = new Div();
        options.addClassName("chat-sidebar-theme-options");

        for (var preference : ThemePreference.values()) {
            options.add(createThemePreferenceButton(preference, state, viewModel));
        }

        var control = new Div(label, options);
        control.addClassName("chat-sidebar-theme-control");
        return control;
    }

    private Div createRailEntry(String target, Component content) {
        var node = new Div();
        node.addClassName("sidebar-rail-node");
        node.getElement().setAttribute("data-rail-node", target);

        var entry = new Div(node, content);
        entry.addClassNames("sidebar-rail-entry", "sidebar-rail-entry-" + target);
        entry.getElement().setAttribute("data-rail-target", target);
        return entry;
    }

    private Button createThemePreferenceButton(ThemePreference preference, ConversationState state, ConversationViewModel viewModel) {
        var button = new Button(switch (preference) {
            case SYSTEM -> "Sistema";
            case LIGHT -> "Claro";
            case DARK -> "Oscuro";
        });
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.addClassName("chat-sidebar-theme-button");
        button.getThemeNames().remove("icon");
        button.getElement().setAttribute("aria-label", "Cambiar tema a %s".formatted(button.getText()));
        button.addClickListener(_ -> viewModel.onThemePreferenceChanged(preference));

        Signal.effect(button, () -> {
            var active = state.themePreference().get() == preference;
            button.getElement().setAttribute("aria-pressed", Boolean.toString(active));
            if (active) {
                button.addClassName("is-active");
            }
            else {
                button.removeClassName("is-active");
            }
        });

        return button;
    }
}
