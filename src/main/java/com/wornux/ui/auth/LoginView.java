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
@PageTitle("Iniciar sesión")
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
        var productLabel = new Span("Tutor Socrático");
        productLabel.addClassName("login-view__product-label");

        var title = new H1("Trabajo real, razonado paso a paso.");
        title.addClassName("login-view__title");

        var description = new Paragraph(
                "Trae una pregunta, un documento o un fragmento de código. El tutor mantiene la conversación anclada en evidencia y guía el siguiente paso sin quitarte el trabajo de las manos.");
        description.addClassName("login-view__description");

        var copy = new Div(productLabel, title, description);
        copy.addClassName("login-view__brand-copy");

        var threadHeader = new Div(new Span("conversación"), new Span("listo para depurar"));
        threadHeader.addClassName("login-view__work-header");

        var userPrompt = new Div(new Span("Estudiante"), new Paragraph("¿Por qué este ciclo se salta el último valor?"));
        userPrompt.addClassName("login-view__work-message");
        userPrompt.addClassName("login-view__work-message--user");

        var tutorResponse = new Div(new Span("Tutor"), new Paragraph(
                "Revisa primero el límite. ¿Qué valor tiene i en la comparación final y qué índice del arreglo intentaría tocar?"));
        tutorResponse.addClassName("login-view__work-message");

        var codeLineOne = new Span("for (int i = 0; i < values.length - 1; i++) {");
        var codeLineTwo = new Span("    sum += values[i];");
        var codeLineThree = new Span("}");
        var codeSample = new Div(codeLineOne, codeLineTwo, codeLineThree);
        codeSample.addClassName("login-view__code-sample");

        var evidence = new Div(new Span("Contexto del documento · 3 fragmentos"));
        evidence.addClassName("login-view__evidence-row");

        var workPreview = new Div(threadHeader, userPrompt, tutorResponse, codeSample, evidence);
        workPreview.addClassName("login-view__work-preview");

        var cherry = new AsciiFrameAnimation("cherry-frames", 147, 18);
        cherry.setBouncing(false);
        cherry.addClassName("login-view__cherry");
        cherry.getElement().setAttribute("aria-hidden", "true");

        var cherryFrame = new Div(cherry);
        cherryFrame.addClassName("login-view__cherry-frame");

        var panel = new Div(copy, workPreview, cherryFrame);
        panel.addClassName("login-view__brand");
        return panel;
    }

    private Component createFormPanel() {
        var title = new H1("Continúa tu aprendizaje");
        title.addClassName("login-view__panel-title");

        var hint = new Paragraph("Vuelve a tus conversaciones, documentos y evaluaciones guardadas.");
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
        form.setTitle("Inicia sesión");
        form.setUsername("Correo");
        form.setPassword("Contraseña");
        form.setSubmit("Entrar");
        form.setForgotPassword("Acceso solo con invitación");
        i18n.setForm(form);

        var error = i18n.getErrorMessage();
        error.setTitle("No pudimos iniciar sesión");
        error.setMessage("Revisa tu correo y tu contraseña, e inténtalo de nuevo.");
        i18n.setErrorMessage(error);

        return i18n;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        if (authenticatedAccountService.currentAccount().isPresent()) {
            beforeEnterEvent.forwardTo(LandingView.class);
            return;
        }
        if (beforeEnterEvent.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            login.setError(true);
        }
    }
}
