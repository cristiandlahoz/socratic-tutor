package com.wornux.ui.auth;

import java.util.Optional;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.wornux.services.onboarding.InvitationService;
import com.wornux.services.onboarding.InvitationStateException;
import com.wornux.services.security.AuthenticatedAccountService;

@Route(value = "invitations/accept", autoLayout = false)
@PageTitle("Accept invitation")
@AnonymousAllowed
public class InvitationAcceptView extends VerticalLayout implements BeforeEnterObserver {

    private final InvitationService invitationService;
    private final AuthenticatedAccountService authenticatedAccountService;
    private final EmailField emailField = new EmailField("Invited email");
    private final TextField firstNameField = new TextField("First name");
    private final TextField lastNameField = new TextField("Last name");
    private final PasswordField passwordField = new PasswordField("Password");
    private final PasswordField confirmPasswordField = new PasswordField("Confirm password");
    private final Div content = new Div();

    public InvitationAcceptView(
            InvitationService invitationService,
            AuthenticatedAccountService authenticatedAccountService) {
        this.invitationService = invitationService;
        this.authenticatedAccountService = authenticatedAccountService;

        addClassName("workspace-view");
        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setDefaultHorizontalComponentAlignment(Alignment.CENTER);

        emailField.setReadOnly(true);
        emailField.setWidthFull();
        firstNameField.setWidthFull();
        lastNameField.setWidthFull();
        passwordField.setWidthFull();
        confirmPasswordField.setWidthFull();
        content.addClassName("workspace-card");
        add(content);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var token = event.getLocation().getQueryParameters().getSingleParameter("token");
        if (token.isEmpty()) {
            event.forwardTo(NoAccessView.class);
            return;
        }
        try {
            var onboarding = invitationService.prepareOnboarding(token.get());
            emailField.setValue(onboarding.invitedEmail());
            var currentAccount = authenticatedAccountService.currentAccount();
            if (currentAccount.isPresent()) {
                if (!currentAccount.get().getEmail().equalsIgnoreCase(onboarding.invitedEmail())) {
                    renderAuthenticatedMismatch(currentAccount.get().getEmail(), onboarding.accountAlreadyExists());
                    return;
                }
                var decision = invitationService.completePendingInvitationForCurrentAccount();
                event.forwardTo(decision.route());
                return;
            }
            render(onboarding.accountAlreadyExists());
        }
        catch (InvitationStateException exception) {
            content.removeAll();
            content.add(new H2("Invitation unavailable"), new Paragraph(exception.getMessage()));
        }
    }

    private void renderAuthenticatedMismatch(String currentEmail, boolean accountAlreadyExists) {
        content.removeAll();

        var title = new H2("Invitation ready for a different account");
        var mismatch = new Paragraph(
                "This invitation is for " + emailField.getValue() + ", but you are signed in as " + currentEmail + ".");
        content.add(title, mismatch);

        if (accountAlreadyExists) {
            var loginButton =
                    new Button("Continue to login", _ -> getUI().ifPresent(ui -> ui.navigate(LoginView.class)));
            loginButton.addThemeVariants(ButtonVariant.PRIMARY);
            content.add(
                new Paragraph("Sign in with the invited email address to continue accepting this invitation."),
                loginButton);
            return;
        }

        content.add(
            new Paragraph(
                    "Sign out or open this invitation in a private window to create the invited account safely."));
    }

    private void render(boolean accountAlreadyExists) {
        content.removeAll();
        if (accountAlreadyExists) {
            var loginButton =
                    new Button("Continue to login", _ -> getUI().ifPresent(ui -> ui.navigate(LoginView.class)));
            loginButton.addThemeVariants(ButtonVariant.PRIMARY);
            content.add(
                new H2("Sign in to accept your invitation"),
                new Paragraph(
                        "This invited email already belongs to an account. Sign in with the same email address to continue."),
                emailField,
                loginButton);
            return;
        }

        var registerButton = new Button("Create account", _ -> onRegister());
        registerButton.addThemeVariants(ButtonVariant.PRIMARY);
        content.add(
            new H2("Complete your invited registration"),
            new Paragraph("Your invitation email becomes your account email. Create your password to continue."),
            emailField,
            firstNameField,
            lastNameField,
            passwordField,
            confirmPasswordField,
            registerButton);
    }

    private void onRegister() {
        try {
            invitationService.registerInvitedAccount(
                firstNameField.getValue(),
                lastNameField.getValue(),
                passwordField.getValue(),
                confirmPasswordField.getValue());
            Notification.show("Account created. Sign in to finish accepting your invitation.");
            getUI().ifPresent(ui -> ui.navigate(LoginView.class));
        }
        catch (InvitationStateException exception) {
            Notification.show(exception.getMessage());
        }
    }
}
