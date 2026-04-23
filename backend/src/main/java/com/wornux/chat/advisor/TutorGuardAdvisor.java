package com.wornux.chat.advisor;

import com.wornux.chat.GuardClassifierService;
import com.wornux.chat.GuardDecision;
import com.wornux.chat.prompt.PromptMessageUtils;
import com.wornux.chat.prompt.TutorPromptResources;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public class TutorGuardAdvisor implements CallAdvisor, StreamAdvisor {

  private static final Logger log = LoggerFactory.getLogger(TutorGuardAdvisor.class);

  private static final Pattern NON_C_TECH_PATTERN =
      Pattern.compile(
          "\\b(java|javascript|typescript|python|kotlin|swift|php|ruby|go|golang|rust|c\\+\\+|c#|\\.net)\\b",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern C_OR_LOGIC_PATTERN =
      Pattern.compile(
          "\\b(c\\b|ansi\\s*c|flow\\s*control|control\\s*flow|control\\s*structures|if|switch|loop|while|for|do\\s*while|"
              + "function|variable|pointer|malloc|free|memory|algorithm|algoritmo|logic|logica|pseudocode|"
              + "pseudocodigo|trace|dry\\s*run|complexity|complejidad)\\b",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern SHORTCUT_REQUEST_PATTERN =
      Pattern.compile(
          "\\b(give\\s+me\\s+the\\s+answer|final\\s+answer|just\\s+the\\s+answer|solve\\s+(it|this)|do\\s+my\\s+homework|"
              + "only\\s+code|no\\s+explanation|dame\\s+la\\s+respuesta|respuesta\\s+final|resuelv(e|elo|eme)|"
              + "haz\\s+mi\\s+tarea|solo\\s+codigo|sin\\s+explicacion)\\b",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern PROMPT_INJECTION_PATTERN =
      Pattern.compile(
          "\\b(ignore\\s+previous\\s+instructions|show\\s+me\\s+your\\s+system\\s+prompt|"
              + "reveal\\s+your\\s+instructions|ignora\\s+las\\s+instrucciones|"
              + "muestrame\\s+tu\\s+prompt\\s+del\\s+sistema|revela\\s+tus\\s+instrucciones)\\b",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern IMPERSONATION_PATTERN =
      Pattern.compile(
          "\\b(i\\s*am\\s*(the\\s+)?(professor|teacher|instructor|admin|administrator|coordinator|evaluator)|"
              + "i'?m\\s*(the\\s+)?(professor|teacher|instructor|admin|administrator|coordinator|evaluator)|"
              + "as\\s+(your\\s+)?(professor|teacher|instructor|admin|administrator|coordinator|evaluator)|"
              + "soy\\s+(el|la)?\\s*(profesor|profesora|admin|administrador|administradora|coordinador|coordinadora|evaluador|evaluadora)|"
              + "como\\s+(profesor|profesora|admin|administrador|administradora|coordinador|coordinadora|evaluador|evaluadora))\\b",
          Pattern.CASE_INSENSITIVE);

  private final int order;
  private final GuardClassifierService guardClassifierService;
  private final TutorPromptResources promptResources;

  public TutorGuardAdvisor(
      int order,
      GuardClassifierService guardClassifierService,
      TutorPromptResources promptResources) {
    this.order = order;
    this.guardClassifierService = guardClassifierService;
    this.promptResources = promptResources;
  }

  @Override
  public org.springframework.ai.chat.client.ChatClientResponse adviseCall(
      ChatClientRequest request, @NonNull CallAdvisorChain chain) {
    String userQuery = PromptMessageUtils.extractLastUserText(request.prompt());

    RuleDecision ruleDecision = ruleDecisionFor(userQuery);
    return chain.nextCall(applySafetyPolicy(request, userQuery, ruleDecision));
  }

  @Override
  public Flux<ChatClientResponse> adviseStream(
      ChatClientRequest request, @NonNull StreamAdvisorChain chain) {
    String userQuery = PromptMessageUtils.extractLastUserText(request.prompt());

    RuleDecision ruleDecision = ruleDecisionFor(userQuery);
    return chain.nextStream(applySafetyPolicy(request, userQuery, ruleDecision));
  }

  ChatClientRequest applySafetyPolicy(
      ChatClientRequest request, String userQuery, RuleDecision ruleDecision) {
    return applyGuardDecision(request, guardDecisionFor(userQuery, ruleDecision));
  }

  RuleDecision ruleDecisionFor(String input) {
    String normalized = normalize(input);
    if (IMPERSONATION_PATTERN.matcher(normalized).find()) {
      return RuleDecision.IMPERSONATION;
    }
    if (isOutOfScope(normalized)) {
      return RuleDecision.OUT_OF_SCOPE;
    }
    if (SHORTCUT_REQUEST_PATTERN.matcher(normalized).find()
        || PROMPT_INJECTION_PATTERN.matcher(normalized).find()) {
      return RuleDecision.NOT_SAFE;
    }
    return RuleDecision.NEEDS_CLASSIFICATION;
  }

  GuardDecision guardDecisionFor(String userQuery, RuleDecision ruleDecision) {
    return switch (ruleDecision) {
      case IMPERSONATION -> GuardDecision.IMPERSONATION;
      case OUT_OF_SCOPE -> GuardDecision.OUT_OF_SCOPE;
      case NOT_SAFE -> GuardDecision.NOT_SAFE;
      case NEEDS_CLASSIFICATION -> classifyGuardDecision(userQuery);
    };
  }

  GuardDecision classifyGuardDecision(String userQuery) {
    try {
      return guardClassifierService.classify(userQuery);
    } catch (RuntimeException ex) {
      log.warn("Guard classifier failed, defaulting to the not-safe guard policy", ex);
      return GuardDecision.NOT_SAFE;
    }
  }

  ChatClientRequest applyGuardDecision(ChatClientRequest request, GuardDecision decision) {
    return switch (decision) {
      case SAFE -> request;
      case NOT_SAFE ->
          appendSystemMessage(request, promptResources.guardNotSafe(), "not_safe_guard");
      case IMPERSONATION ->
          appendSystemMessage(
              request, promptResources.guardImpersonation(), "impersonation_handling_mode");
      case OUT_OF_SCOPE ->
          appendSystemMessage(
              request, promptResources.guardOutOfScope(), "out_of_scope_handling_mode");
    };
  }

  private ChatClientRequest appendSystemMessage(
      ChatClientRequest request, String systemMessage, String policyMode) {
    List<Message> messages = new ArrayList<>(request.prompt().getInstructions());
    messages.add(new SystemMessage(systemMessage));

    var promptBuilder = Prompt.builder().messages(messages);
    var options = request.prompt().getOptions();
    if (!Objects.isNull(options)) {
      promptBuilder.chatOptions(options);
    }

    return request
        .mutate()
        .prompt(promptBuilder.build())
        .context("policy_mode", policyMode)
        .build();
  }

  private static boolean isOutOfScope(String normalized) {
    boolean mentionsNonCLanguage = NON_C_TECH_PATTERN.matcher(normalized).find();
    boolean mentionsAllowedTopic = C_OR_LOGIC_PATTERN.matcher(normalized).find();
    return mentionsNonCLanguage && !mentionsAllowedTopic;
  }

  private static String normalize(String input) {
    return input == null ? "" : input.toLowerCase(Locale.ROOT).trim();
  }

  @Override
  public @NullUnmarked String getName() {
    return "tutor-guard-advisor";
  }

  @Override
  public int getOrder() {
    return this.order;
  }

  enum RuleDecision {
    NOT_SAFE,
    IMPERSONATION,
    OUT_OF_SCOPE,
    NEEDS_CLASSIFICATION
  }
}
