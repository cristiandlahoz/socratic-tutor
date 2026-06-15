package com.wornux.ui.auth;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.wornux.ui.chat.AsciiFrameAnimation;

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

    var eyebrow = new Span("Acceso académico");
    eyebrow.addClassName("login-view__eyebrow");

    var title = new H1("Tutor Socrático");
    title.addClassName("login-view__title");

    var description = new Paragraph(
        "Explora ideas, resolvé dudas y aprendé algoritmia con preguntas guiadas, paso a paso.");
    description.addClassName("login-view__description");

    var marker = new Span("Método socrático · práctica deliberada · razonamiento claro");
    marker.addClassName("login-view__marker");

    var copy = new Div(eyebrow, title, description, marker);
    copy.addClassName("login-view__brand-copy");

    var crow = new AsciiFrameAnimation("crow3-frames", 240, 30);
    crow.addClassName("login-view__crow");

    var crowFrame = new Div(crow);
    crowFrame.addClassName("login-view__crow-frame");

    var panel = new Div(rail, copy, crowFrame);
    panel.addClassName("login-view__brand");
    return panel;
  }

  private Component createFormPanel() {
    var label = new Span("Sesión segura");
    label.addClassName("login-view__panel-label");

    var title = new H1("Continuá tu aprendizaje");
    title.addClassName("login-view__panel-title");

    var hint = new Paragraph("Ingresa con tus credenciales para volver a tus conversaciones y evaluaciones.");
    hint.addClassName("login-view__panel-hint");

    var header = new Div(label, title, hint);
    header.addClassName("login-view__panel-header");

    var formCard = new Div(header, login);
    formCard.addClassName("login-view__form-card");

    var panel = new Div(formCard);
    panel.addClassName("login-view__panel");
    return panel;
  }

  private LoginI18n createLoginI18n() {
    var i18n = LoginI18n.createDefault();

    var form = i18n.getForm();
    form.setTitle("Ingresar");
    form.setUsername("Usuario");
    form.setPassword("Contraseña");
    form.setSubmit("Entrar");
    form.setForgotPassword("Olvidé mi contraseña");
    i18n.setForm(form);

    var error = i18n.getErrorMessage();
    error.setTitle("No pudimos iniciar sesión");
    error.setMessage("Revisá tu usuario y contraseña, y volvé a intentarlo.");
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
