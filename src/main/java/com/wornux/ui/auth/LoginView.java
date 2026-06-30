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
import com.wornux.ui.css.UiCss;

@Route(value = "login", autoLayout = false)
@PageTitle("Iniciar sesión")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm login = new LoginForm();
    private final AuthenticatedAccountService authenticatedAccountService;

    public LoginView(AuthenticatedAccountService authenticatedAccountService) {
        this.authenticatedAccountService = authenticatedAccountService;
        UiCss.LOGIN_VIEW.addTo(this);
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        setMargin(false);

        login.setAction("login");
        login.setI18n(createLoginI18n());
        UiCss.LOGIN_VIEW_FORM.addTo(login);

        add(createShell());
    }

    private Component createShell() {
        var shell = new Div(createBrandPanel(), createFormPanel());
        UiCss.LOGIN_VIEW_SHELL.addTo(shell);
        return shell;
    }

    private Component createBrandPanel() {
        var productLabel = new Span("Tutor Socrático");
        UiCss.LOGIN_VIEW_PRODUCT_LABEL.addTo(productLabel);

        var title = new H1("Trabajo real, razonado paso a paso.");
        UiCss.LOGIN_VIEW_TITLE.addTo(title);

        var description = new Paragraph(
                "Trae una pregunta, un documento o un fragmento de código. El tutor mantiene la conversación anclada en evidencia y guía el siguiente paso sin quitarte el trabajo de las manos.");
        UiCss.LOGIN_VIEW_DESCRIPTION.addTo(description);

        var copy = new Div(productLabel, title, description);
        UiCss.LOGIN_VIEW_BRAND_COPY.addTo(copy);

        var threadHeader = new Div(new Span("conversación"), new Span("listo para depurar"));
        UiCss.LOGIN_VIEW_WORK_HEADER.addTo(threadHeader);

        var userPrompt = new Div(new Span("Estudiante"), new Paragraph("¿Por qué este ciclo se salta el último valor?"));
        UiCss.LOGIN_VIEW_WORK_MESSAGE.addTo(userPrompt);
        UiCss.LOGIN_VIEW_WORK_MESSAGE_USER.addTo(userPrompt);

        var tutorResponse = new Div(new Span("Tutor"), new Paragraph(
                "Revisa primero el límite. ¿Qué valor tiene i en la comparación final y qué índice del arreglo intentaría tocar?"));
        UiCss.LOGIN_VIEW_WORK_MESSAGE.addTo(tutorResponse);

        var codeLineOne = new Span("for (int i = 0; i < values.length - 1; i++) {");
        var codeLineTwo = new Span("    sum += values[i];");
        var codeLineThree = new Span("}");
        var codeSample = new Div(codeLineOne, codeLineTwo, codeLineThree);
        UiCss.LOGIN_VIEW_CODE_SAMPLE.addTo(codeSample);

        var evidence = new Div(new Span("Contexto del documento · 3 fragmentos"));
        UiCss.LOGIN_VIEW_EVIDENCE_ROW.addTo(evidence);

        var workPreview = new Div(threadHeader, userPrompt, tutorResponse, codeSample, evidence);
        UiCss.LOGIN_VIEW_WORK_PREVIEW.addTo(workPreview);

        var cherry = new AsciiFrameAnimation("cherry-frames", 147, 18);
        cherry.setBouncing(false);
        UiCss.LOGIN_VIEW_CHERRY.addTo(cherry);
        cherry.getElement().setAttribute("aria-hidden", "true");

        var cherryFrame = new Div(cherry);
        UiCss.LOGIN_VIEW_CHERRY_FRAME.addTo(cherryFrame);

        var panel = new Div(copy, workPreview, cherryFrame);
        UiCss.LOGIN_VIEW_BRAND.addTo(panel);
        return panel;
    }

    private Component createFormPanel() {
        var title = new H1("Continúa tu aprendizaje");
        UiCss.LOGIN_VIEW_PANEL_TITLE.addTo(title);

        var hint = new Paragraph("Vuelve a tus conversaciones, documentos y evaluaciones guardadas.");
        UiCss.LOGIN_VIEW_PANEL_HINT.addTo(hint);

        var header = new Div(title, hint);
        UiCss.LOGIN_VIEW_PANEL_HEADER.addTo(header);

        var crow = new AsciiFrameAnimation("crow3-frames", 240, 30);
        UiCss.LOGIN_VIEW_CROW.addTo(crow);
        crow.getElement().setAttribute("aria-hidden", "true");

        var crowFrame = new Div(crow);
        UiCss.LOGIN_VIEW_CROW_FRAME.addTo(crowFrame);

        var formCard = new Div(crowFrame, header, login);
        UiCss.LOGIN_VIEW_FORM_CARD.addTo(formCard);

        var panel = new Div(formCard);
        UiCss.LOGIN_VIEW_PANEL.addTo(panel);
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
