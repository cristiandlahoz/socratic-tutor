package com.wornux.ui.auth;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.wornux.ui.conversation.AsciiFrameAnimation;

@Route(value = "login", autoLayout = false)
@PageTitle("Login")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm login = new LoginForm();

    public LoginView() {
        addClassName("login-view");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        setMargin(false);

        login.setAction("login");
        login.setI18n(createLoginI18n());
        login.addClassName("login-view__form");

        add(createShell());
    }

    private Component createShell() {
        var shell = new Div(createBrandPanel(), createFormPanel());
        shell.addClassName("login-view__shell");
        return shell;
    }

    private Component createBrandPanel() {
        var rail = new Div();
        rail.addClassName("login-view__rail");

        var title = new H1("Socratic Tutor");
        title.addClassName("login-view__title");

        var description = new Paragraph("Explore ideas, ask better questions, and learn algorithms step by step.");
        description.addClassName("login-view__description");

        var copy = new Div(title, description);
        copy.addClassName("login-view__brand-copy");

        var panel = new Div(rail, copy);
        panel.addClassName("login-view__brand");
        return panel;
    }

    private Component createFormPanel() {
        var title = new H1("Continue your learning");
        title.addClassName("login-view__panel-title");

        var hint = new Paragraph("Return to your saved conversations, documents, and evaluations.");
        hint.addClassName("login-view__panel-hint");

        var header = new Div(title, hint);
        header.addClassName("login-view__panel-header");

        var crow = new AsciiFrameAnimation("crow3-frames", 240, 30);
        crow.addClassName("login-view__crow");
        crow.getElement().setAttribute("aria-hidden", "true");

        var crowFrame = new Div(crow);
        crowFrame.addClassName("login-view__crow-frame");

        var formCard = new Div(crowFrame, header, login);
        formCard.addClassName("login-view__form-card");

        var panel = new Div(formCard);
        panel.addClassName("login-view__panel");
        return panel;
    }

    private LoginI18n createLoginI18n() {
        var i18n = LoginI18n.createDefault();

        var form = i18n.getForm();
        form.setTitle("Sign in");
        form.setUsername("Email or username");
        form.setPassword("Password");
        form.setSubmit("Sign in");
        form.setForgotPassword("Invitation-only access");
        i18n.setForm(form);

        var error = i18n.getErrorMessage();
        error.setTitle("We could not sign you in");
        error.setMessage("Check your email or username and password, then try again.");
        i18n.setErrorMessage(error);

        return i18n;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        if (beforeEnterEvent.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            login.setError(true);
        }
    }
}
