package com.wornux.usecases.uc003;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.onboarding.InvitationTargetRole;
import com.wornux.services.onboarding.InvitationService;
import com.wornux.services.onboarding.OnboardingStart;
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.ui.auth.InvitationAcceptView;
import com.wornux.ui.auth.LoginView;
import org.junit.jupiter.api.Test;

class UC003InvitationAcceptViewTest {

    @Test
    void br12_br57_br58_br59_invitationAcceptViewShowsRegistrationFlowForInvitedSignup() {
        var invitationService = mock(InvitationService.class);
        var authenticatedAccountService = mock(AuthenticatedAccountService.class);
        var view = new InvitationAcceptView(invitationService, authenticatedAccountService);
        var event = beforeEnterEvent("signup-token");
        when(invitationService.prepareOnboarding("signup-token"))
                .thenReturn(new OnboardingStart(1L, "student@test.local", InvitationTargetRole.STUDENT, false));
        when(authenticatedAccountService.currentAccount()).thenReturn(Optional.empty());

        view.beforeEnter(event);

        verify(invitationService, never()).completePendingInvitationForCurrentAccount();
        assertTrue(texts(view).contains("Complete your invited registration"));
        assertTrue(
            texts(view)
                    .contains("Your invitation email becomes your account email. Create your password to continue."));
        assertEquals(
            List.of("student@test.local"),
            descendantsOfType(view, EmailField.class).stream().map(EmailField::getValue).toList());
        assertEquals(2, descendantsOfType(view, TextField.class).size());
        assertEquals(2, descendantsOfType(view, PasswordField.class).size());
        assertTrue(buttonTexts(view).contains("Create account"));
    }

    @Test
    void br13_br62_invitationAcceptViewShowsLoginPathForExistingInvitedAccount() {
        var invitationService = mock(InvitationService.class);
        var authenticatedAccountService = mock(AuthenticatedAccountService.class);
        var view = new InvitationAcceptView(invitationService, authenticatedAccountService);
        var event = beforeEnterEvent("login-token");
        when(invitationService.prepareOnboarding("login-token"))
                .thenReturn(new OnboardingStart(1L, "professor@test.local", InvitationTargetRole.PROFESSOR, true));
        when(authenticatedAccountService.currentAccount()).thenReturn(Optional.empty());

        view.beforeEnter(event);

        verify(invitationService, never()).completePendingInvitationForCurrentAccount();
        assertTrue(texts(view).contains("Sign in to accept your invitation"));
        assertTrue(
            texts(view).contains(
                "This invited email already belongs to an account. Sign in with the same email address to continue."));
        assertTrue(buttonTexts(view).contains("Continue to login"));
    }

    @Test
    void br13_br14_br63_invitationAcceptViewKeepsMismatchedAuthenticatedAccountOutOfUnavailableState() {
        var invitationService = mock(InvitationService.class);
        var authenticatedAccountService = mock(AuthenticatedAccountService.class);
        var view = new InvitationAcceptView(invitationService, authenticatedAccountService);
        var event = beforeEnterEvent("mismatch-token");
        when(invitationService.prepareOnboarding("mismatch-token"))
                .thenReturn(new OnboardingStart(1L, "invited@test.local", InvitationTargetRole.TENANT_ADMIN, true));
        when(authenticatedAccountService.currentAccount()).thenReturn(Optional.of(account("other@test.local")));

        view.beforeEnter(event);

        verify(invitationService, never()).completePendingInvitationForCurrentAccount();
        verify(event, never()).forwardTo(any(Class.class));
        verify(event, never()).forwardTo(anyString());
        assertTrue(texts(view).contains("Invitation ready for a different account"));
        assertTrue(
            texts(view)
                    .contains("This invitation is for invited@test.local, but you are signed in as other@test.local."));
        assertTrue(
            texts(view).contains("Sign in with the invited email address to continue accepting this invitation."));
        assertFalse(texts(view).contains("Invitation unavailable"));
        assertTrue(buttonTexts(view).contains("Continue to login"));
    }

    private static BeforeEnterEvent beforeEnterEvent(String token) {
        var event = mock(BeforeEnterEvent.class);
        when(event.getLocation()).thenReturn(new Location("invitations/accept?token=" + token));
        return event;
    }

    private static Account account(String email) {
        var account = new Account();
        account.setId(UUID.randomUUID());
        account.setEmail(email);
        return account;
    }

    private static List<String> texts(Component component) {
        return stream(component).filter(HasText.class::isInstance)
                .map(HasText.class::cast)
                .map(HasText::getText)
                .filter(text -> text != null && !text.isBlank())
                .toList();
    }

    private static List<String> buttonTexts(Component component) {
        return descendantsOfType(component, Button.class).stream().map(Button::getText).toList();
    }

    private static <T extends Component> List<T> descendantsOfType(Component component, Class<T> type) {
        return stream(component).filter(type::isInstance).map(type::cast).toList();
    }

    private static Stream<Component> stream(Component component) {
        return Stream
                .concat(Stream.of(component), component.getChildren().flatMap(UC003InvitationAcceptViewTest::stream));
    }
}
