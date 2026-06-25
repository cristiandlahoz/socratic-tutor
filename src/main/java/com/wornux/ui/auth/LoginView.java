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
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.ui.conversation.AsciiFrameAnimation;

@Route(value = "login", autoLayout = false)
@PageTitle("Login")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm login = new LoginForm();
    private final AuthenticatedAccountService authenticatedAccountService;

    public LoginView(AuthenticatedAccountService authenticatedAccountService) {
        this.authenticatedAccountService = authenticatedAccountService;
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
        var productLabel = new Span("Socratic Tutor");
        productLabel.addClassName("login-view__product-label");

        var title = new H1("Real work, reasoned through.");
        title.addClassName("login-view__title");

        var description = new Paragraph(
                "Bring a question, a document, or a piece of code. The tutor keeps the thread grounded in evidence and guides the next step without taking the work away from you.");
        description.addClassName("login-view__description");

        var copy = new Div(productLabel, title, description);
        copy.addClassName("login-view__brand-copy");

        var threadHeader = new Div(new Span("conversation"), new Span("debug-ready"));
        threadHeader.addClassName("login-view__work-header");

        var userPrompt = new Div(new Span("Student"), new Paragraph("Why does this loop skip the last value?"));
        userPrompt.addClassName("login-view__work-message");
        userPrompt.addClassName("login-view__work-message--user");

        var tutorResponse = new Div(new Span("Tutor"), new Paragraph(
                "Trace the boundary first. What value does i hold on the final comparison, and which array index would that touch?"));
        tutorResponse.addClassName("login-view__work-message");

        var codeLineOne = new Span("for (int i = 0; i < values.length - 1; i++) {");
        var codeLineTwo = new Span("    sum += values[i];");
        var codeLineThree = new Span("}");
        var codeSample = new Div(codeLineOne, codeLineTwo, codeLineThree);
        codeSample.addClassName("login-view__code-sample");

        var evidence = new Div(new Span("Document context · 3 passages"));
        evidence.addClassName("login-view__evidence-row");

        var workPreview = new Div(threadHeader, userPrompt, tutorResponse, codeSample, evidence);
        workPreview.addClassName("login-view__work-preview");

        var cherry = new AsciiFrameAnimation("cherry-frames", 147, 24);
        cherry.addClassName("login-view__cherry");
        cherry.getElement().setAttribute("aria-hidden", "true");

        var cherryFrame = new Div(cherry);
        cherryFrame.addClassName("login-view__cherry-frame");

        var panel = new Div(copy, workPreview, cherryFrame);
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
        if (authenticatedAccountService.currentAccount().isPresent()) {
            beforeEnterEvent.forwardTo("");
            return;
        }
        if (beforeEnterEvent.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            login.setError(true);
        }
    }
}
