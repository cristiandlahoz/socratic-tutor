package com.wornux.ui;

import java.util.Comparator;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.router.PreserveOnRefresh;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.spring.annotation.RouteScopeOwner;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.wornux.data.enums.ThemePreference;
import com.wornux.security.authorization.ActiveContextHolder;
import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.services.context.ContextDiscoveryService;
import com.wornux.services.context.ContextSelectionService;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.components.ProfileDrawerCard;
import com.wornux.ui.components.ToggleIcon;
import com.wornux.ui.components.sidebar.ConversationHistoryDrawer;
import com.wornux.ui.components.sidebar.WorkspaceDrawerNavigation;
import com.wornux.ui.conversation.ConversationState;
import com.wornux.ui.conversation.ConversationViewModel;
import com.wornux.ui.css.CssClass;
import com.wornux.ui.css.UiCss;
import com.wornux.ui.navigation.NavigationEntry;
import com.wornux.ui.navigation.NavigationRegistry;
import jakarta.annotation.security.PermitAll;
import org.springframework.core.annotation.AnnotationUtils;

@Layout
@PreserveOnRefresh
@PermitAll
public class MainLayout extends AppLayout {

    private final ConversationState state;
    private final ConversationViewModel viewModel;
    private final transient AuthenticatedUserContextUtils authenticatedUserContextUtils;
    private final transient AuthenticationContext authenticationContext;
    private final transient ActiveContextHolder activeContextHolder;
    private final transient AuthorizationService authorizationService;
    private final transient ContextDiscoveryService contextDiscoveryService;
    private final transient ContextSelectionService contextSelectionService;
    private final transient NavigationRegistry navigationRegistry;
    private final transient WorkspaceRoutingService workspaceRoutingService;
    private final Div drawerContent = new Div();

    public MainLayout(
            @RouteScopeOwner(MainLayout.class) ConversationState state,
            @RouteScopeOwner(MainLayout.class) ConversationViewModel viewModel,
            AuthenticatedUserContextUtils authenticatedUserContextUtils,
            AuthenticationContext authenticationContext,
            ActiveContextHolder activeContextHolder,
            AuthorizationService authorizationService,
            ContextDiscoveryService contextDiscoveryService,
            ContextSelectionService contextSelectionService,
            NavigationRegistry navigationRegistry,
            WorkspaceRoutingService workspaceRoutingService) {
        this.state = state;
        this.viewModel = viewModel;
        this.authenticatedUserContextUtils = authenticatedUserContextUtils;
        this.authenticationContext = authenticationContext;
        this.activeContextHolder = activeContextHolder;
        this.authorizationService = authorizationService;
        this.contextDiscoveryService = contextDiscoveryService;
        this.contextSelectionService = contextSelectionService;
        this.navigationRegistry = navigationRegistry;
        this.workspaceRoutingService = workspaceRoutingService;

        setPrimarySection(Section.DRAWER);
        addToNavbar(createDrawerToggle(UiCss.SHELL_DRAWER_TOGGLE, "Abrir menú"));
        this.viewModel.initializeShellState();

        UiCss.SHELL_DRAWER_CONTENT.addTo(drawerContent);
        UiCss.APP_SIDEBAR.addTo(drawerContent);
        drawerContent.setSizeFull();
        refreshDrawerContent();

        var drawerScroller = new Scroller(drawerContent, Scroller.ScrollDirection.NONE);
        drawerScroller.setSizeFull();
        UiCss.SHELL_DRAWER_SCROLLER.addTo(drawerScroller);
        addToDrawer(drawerScroller);
    }

    private void refreshDrawerContent() {
        drawerContent.removeAll();
        drawerContent.add(createBrandSection());

        var currentAccount = authenticatedUserContextUtils.currentAccount();
        if (currentAccount.isEmpty()) {
            return;
        }

        var account = currentAccount.get();
        var entries = navigationRegistry.entries().stream()
                .filter(entry -> activeContextHolder.current()
                        .map(context -> context.level().ordinal() >= entry.minimumContextLevel().ordinal())
                        .orElse(false))
                .filter(entry -> authorizationService.can(entry.requiredPermission()))
                .filter(entry -> entry.workspaceDestination() == null
                        || workspaceRoutingService.canAccessWorkspace(account, entry.workspaceDestination()))
                .sorted(Comparator.comparingInt(NavigationEntry::order))
                .toList();
        drawerContent.add(new WorkspaceDrawerNavigation(entries));
        if (entries.stream().anyMatch(entry -> entry.requiredPermission().code().startsWith("conversation:"))) {
            drawerContent.add(new ConversationHistoryDrawer(state, viewModel));
        }
        currentAccount.map(user -> new ProfileDrawerCard(
            user,
            createThemePreferenceControl(state, viewModel),
            activeContextHolder,
            contextDiscoveryService,
            contextSelectionService,
            authenticationContext::logout))
                .ifPresent(drawerContent::add);
    }

    public void refreshAfterRbacChange() {
        refreshDrawerContent();
    }

    public boolean currentRouteAllowed() {
        var content = getContent();
        if (content == null) {
            return true;
        }
        var permission = AnnotationUtils.findAnnotation(content.getClass(), RequiresPermission.class);
        if (permission == null) {
            return true;
        }
        try {
            return authorizationService.can(permission.value())
                    && (permission.workspace() == com.wornux.services.workspace.WorkspaceDestination.NO_ACCESS
                            || workspaceRoutingService.canAccessWorkspace(
                                    authenticatedUserContextUtils.requireCurrentAccount(),
                                    permission.workspace()));
        }
        catch (RuntimeException _) {
            return false;
        }
    }

    private Div createBrandSection() {
        var appTitle = new SvgIcon("/icons/tutor-socratico-logo-vector.svg");
        UiCss.APP_SIDEBAR_BRAND_TITLE.addTo(appTitle);
        appTitle.getElement().setAttribute("aria-label", "Tutor Socrático");

        var appDescription = "Tutor para explorar ideas, resolver dudas y aprender introducción a la algoritmia "
                + "con conversaciones guiadas.";
        var appInfo = new Button(new SvgIcon("/icons/IconInfo.svg"));
        appInfo.addThemeVariants(ButtonVariant.TERTIARY);
        UiCss.APP_SIDEBAR_BRAND_INFO.addTo(appInfo);
        appInfo.setAriaLabel("Acerca de Tutor Socrático");
        appInfo.setTooltipText(appDescription);

        var appTitleGroup = new Div(appTitle, appInfo);
        UiCss.APP_SIDEBAR_BRAND_TITLE_GROUP.addTo(appTitleGroup);

        var drawerToggle = createDrawerToggle(UiCss.SHELL_DRAWER_TOGGLE_INSIDE, "Cerrar menú");

        var appTitleRow = new Div(appTitleGroup, drawerToggle);
        UiCss.APP_SIDEBAR_BRAND_ROW.addTo(appTitleRow);

        var appHeader = new Div(appTitleRow);
        UiCss.APP_SIDEBAR_BRAND.addTo(appHeader);
        return appHeader;
    }

    private DrawerToggle createDrawerToggle(CssClass className, String ariaLabel) {
        var toggle = new DrawerToggle();
        toggle.setIcon(new ToggleIcon());
        toggle.addThemeVariants(ButtonVariant.TERTIARY);
        className.addTo(toggle);
        toggle.setAriaLabel(ariaLabel);
        return toggle;
    }

    private Div createThemePreferenceControl(ConversationState state, ConversationViewModel viewModel) {
        var label = new Span("Tema");
        UiCss.THEME_SWITCHER_LABEL.addTo(label);

        var options = new Div();
        UiCss.THEME_SWITCHER_OPTIONS.addTo(options);

        for (var preference : ThemePreference.values()) {
            options.add(createThemePreferenceButton(preference, state, viewModel));
        }

        var control = new Div(label, options);
        UiCss.THEME_SWITCHER.addTo(control);
        return control;
    }

    private Button createThemePreferenceButton(
            ThemePreference preference,
            ConversationState state,
            ConversationViewModel viewModel) {
        var button = new Button(switch (preference) {
            case SYSTEM -> "Sistema";
            case LIGHT -> "Claro";
            case DARK -> "Oscuro";
        });
        button.addThemeVariants(ButtonVariant.TERTIARY);
        UiCss.THEME_SWITCHER_BUTTON.addTo(button);
        button.getThemeNames().remove("icon");
        button.getElement().setAttribute("aria-label", "Cambiar tema a %s".formatted(button.getText()));
        button.addClickListener(_ -> viewModel.onThemePreferenceChanged(preference));

        Signal.effect(button, () -> {
            var active = state.themePreference().get() == preference;
            button.getElement().setAttribute("aria-pressed", Boolean.toString(active));
            if (active) {
                UiCss.ACTIVE.addTo(button);
            }
            else {
                button.getElement().getClassList().remove(UiCss.ACTIVE.value());
            }
        });

        return button;
    }
}
