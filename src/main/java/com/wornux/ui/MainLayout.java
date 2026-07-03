package com.wornux.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.router.PreserveOnRefresh;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.spring.annotation.RouteScopeOwner;
import com.vaadin.flow.spring.security.AuthenticationContext;
import java.util.Comparator;

import com.wornux.data.enums.ThemePreference;
import com.wornux.security.authorization.ActiveContextHolder;
import com.wornux.security.authorization.AuthorizationService;
import com.wornux.services.context.ContextDiscoveryService;
import com.wornux.services.context.ContextSelectionService;
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.ui.components.ProfileDrawerCard;
import com.wornux.ui.components.ToggleIcon;
import com.wornux.ui.components.sidebar.ConversationHistoryDrawer;
import com.wornux.ui.components.sidebar.SidebarDividerLine;
import com.wornux.ui.components.sidebar.WorkspaceDrawerNavigation;
import com.wornux.ui.conversation.ConversationState;
import com.wornux.ui.conversation.ConversationViewModel;
import com.wornux.ui.css.CssClass;
import com.wornux.ui.css.UiCss;
import com.wornux.ui.navigation.NavigationEntry;
import com.wornux.ui.navigation.NavigationRegistry;
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
            AuthenticationContext authenticationContext,
            ActiveContextHolder activeContextHolder,
            AuthorizationService authorizationService,
            ContextDiscoveryService contextDiscoveryService,
            ContextSelectionService contextSelectionService,
            NavigationRegistry navigationRegistry) {
        setPrimarySection(Section.DRAWER);
        addToNavbar(createDrawerToggle(UiCss.SHELL_DRAWER_TOGGLE, "Abrir menú"));
        this.viewModel = viewModel;
        this.viewModel.initializeShellState();

        var drawerContent = new Div();
        UiCss.SHELL_DRAWER_CONTENT.addTo(drawerContent);
        UiCss.APP_SIDEBAR.addTo(drawerContent);
        drawerContent.setSizeFull();
        drawerContent.add(new SidebarDividerLine());

        drawerContent.add(createBrandSection());

        var currentAccount = authenticatedAccountService.currentAccount();

        if (currentAccount.isPresent()) {
            var entries = navigationRegistry.entries().stream()
                    .filter(entry -> activeContextHolder.current()
                            .map(context -> context.level().ordinal() >= entry.minimumContextLevel().ordinal())
                            .orElse(false))
                    .filter(entry -> authorizationService.can(entry.requiredPermission()))
                    .sorted(Comparator.comparingInt(NavigationEntry::order))
                    .toList();
            drawerContent.add(new WorkspaceDrawerNavigation(entries));
            if (entries.stream().anyMatch(entry -> entry.requiredPermission().code().startsWith("conversation:"))) {
                drawerContent.add(new ConversationHistoryDrawer(state, this.viewModel));
            }
            currentAccount.map(user -> new ProfileDrawerCard(
                user,
                createThemePreferenceControl(state, this.viewModel),
                activeContextHolder,
                contextDiscoveryService,
                contextSelectionService,
                authenticationContext::logout))
                    .ifPresent(drawerContent::add);
        }

        var drawerScroller = new Scroller(drawerContent, Scroller.ScrollDirection.NONE);
        drawerScroller.setSizeFull();
        UiCss.SHELL_DRAWER_SCROLLER.addTo(drawerScroller);
        addToDrawer(drawerScroller);
    }

    private Div createBrandSection() {
        var appTitle = new H1("Tutor Socrático");
        UiCss.APP_SIDEBAR_BRAND_TITLE.addTo(appTitle);

        var appDescription = "Tutor para explorar ideas, resolver dudas y aprender introducción a la algoritmia "
                + "con conversaciones guiadas.";
        var appInfo = new Button(new Icon(VaadinIcon.INFO_CIRCLE_O));
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

    private Button createThemePreferenceButton(ThemePreference preference, ConversationState state, ConversationViewModel viewModel) {
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
