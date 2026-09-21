package org.jahia.modules.formidable.engine.fieldactions;

import org.jahia.modules.formidable.engine.actions.FieldEscaper;
import org.jahia.modules.formidable.engine.actions.TemplateInterpolator;
import org.jahia.modules.formidable.engine.api.FieldAction;
import org.jahia.modules.formidable.engine.api.FieldActionRequest;
import org.jahia.modules.formidable.engine.api.FieldActionResult;
import org.jahia.modules.formidable.engine.api.FmdbProperty;
import org.jahia.modules.formidable.engine.fieldactions.ResolvedFieldAction.Trigger;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Runs a field's actions against one candidate value, in list order — the same code for the pre-check the browser
 * asks for and for the pipeline's step 11b, with the same verdict cache, so that a value the browser was told was
 * fine costs no second provider call at submission.
 *
 * <p>For each action the trigger and the severity filters decide whether it runs at all; then a Java
 * {@link FieldAction} registered for the node type is executed, and failing that the node's {@code hidden.execute}
 * view is rendered and its JSON verdict read. An {@code ACCEPT} moves on; a {@code REJECT} becomes one message for
 * the visitor — the contributor's {@code rejectionMessage} in the visitor's locale, {@code ${value}} and the other
 * fields interpolated with HTML escaping — and stops the run when the action blocks, since the first blocking
 * refusal wins; an {@code UNAVAILABLE} is what the contributor's {@code whenUnavailable} says it is. Whatever an
 * action or a view throws counts as unavailable and is logged: the visitor never sees a stack trace.</p>
 *
 * <p>Every node is read in {@code live} in a system session: the submitter has no reason to have read access to
 * the action nodes, and the message's language is the session's locale.</p>
 */
public final class FieldActionDispatcher {

    /** The result of a run: whether a blocking action refused the value, and every message to show. */
    public record Outcome(boolean blocked, List<FieldActionMessage> messages) {
        public Outcome {
            messages = messages == null ? List.of() : List.copyOf(messages);
        }

        public static Outcome accepted() {
            return new Outcome(false, List.of());
        }
    }

    static final String WORKSPACE_LIVE = "live";
    static final String BUNDLE = "resources.formidable-engine";
    static final String DEFAULT_MESSAGE_KEY = "fmdbmix_fieldActionFeedback.rejectionMessage.default";
    static final String DEFAULT_MESSAGE = "This value could not be verified.";
    /** The interpolation name of the candidate value itself, beside the other fields' names. */
    static final String VALUE_PLACEHOLDER = "value";

    private static final Logger log = LoggerFactory.getLogger(FieldActionDispatcher.class);

    private final Supplier<List<FieldAction>> javaActions;
    private final ViewRenderer viewRenderer;
    private final VerdictCache cache;
    private final Supplier<Duration> cacheTtl;
    /** The repository access, for a system session in {@code live} — a seam for the tests, which have no repository. */
    private final Supplier<JCRTemplate> jcrTemplate;

    /** The runtime dispatcher: the registered Java actions, the render service for the views, a system session in live. */
    public FieldActionDispatcher(Supplier<List<FieldAction>> javaActions, VerdictCache cache, Supplier<Duration> cacheTtl) {
        this(javaActions, new RenderServiceViewRenderer(), cache, cacheTtl, JCRTemplate::getInstance);
    }

    /** The runtime dispatcher over another repository access — the pipeline tests hand over a fake one. */
    public FieldActionDispatcher(Supplier<List<FieldAction>> javaActions, VerdictCache cache, Supplier<Duration> cacheTtl,
                                 Supplier<JCRTemplate> jcrTemplate) {
        this(javaActions, new RenderServiceViewRenderer(), cache, cacheTtl, jcrTemplate);
    }

    FieldActionDispatcher(Supplier<List<FieldAction>> javaActions, ViewRenderer viewRenderer, VerdictCache cache,
                          Supplier<Duration> cacheTtl, Supplier<JCRTemplate> jcrTemplate) {
        this.javaActions = javaActions;
        this.viewRenderer = viewRenderer;
        this.cache = cache;
        this.cacheTtl = cacheTtl;
        this.jcrTemplate = jcrTemplate;
    }

    private <T> T inLive(Locale locale, JCRCallback<T> callback) throws RepositoryException {
        return jcrTemplate.get().doExecuteWithSystemSessionAsUser(null, WORKSPACE_LIVE, locale, callback);
    }

    /**
     * @param req           the visitor's request, the context a JavaScript action renders in; may be {@code null}
     * @param resp          the visitor's response; may be {@code null}
     * @param request       the value under judgement
     * @param actions       the field's actions, in list order
     * @param triggers      the triggers to run — a blur pre-check runs the blur actions, a submit runs them all
     * @param blockingOnly  the pipeline's rule: only an action whose refusal blocks runs again at submission
     * @param interpolation the submitted values the message may interpolate, by field name; {@code ${value}} is added
     */
    public Outcome run(HttpServletRequest req, HttpServletResponse resp, FieldActionRequest request,
                       List<ResolvedFieldAction> actions, Set<Trigger> triggers, boolean blockingOnly,
                       Map<String, List<String>> interpolation) {
        List<FieldActionMessage> messages = new ArrayList<>();
        for (ResolvedFieldAction action : actions) {
            if (!triggers.contains(action.trigger()) || (blockingOnly && !action.blocking())) {
                continue;
            }
            FieldActionResult result = verdict(req, resp, request, action);
            if (result.verdict() == FieldActionResult.Verdict.ACCEPT) {
                continue;
            }
            if (result.verdict() == FieldActionResult.Verdict.UNAVAILABLE) {
                if (action.whenUnavailable() == ResolvedFieldAction.Unavailable.ACCEPT) {
                    log.info("[FieldActionDispatcher] Field action {} ({}) could not run on field '{}' of form {}: {} — the value is accepted, as the contributor set",
                            action.id(), action.nodeType(), request.fieldName(), request.formId(), result.detail());
                    continue;
                }
                log.warn("[FieldActionDispatcher] Field action {} ({}) could not run on field '{}' of form {}: {} — the value is refused, as the contributor set",
                        action.id(), action.nodeType(), request.fieldName(), request.formId(), result.detail());
            }
            messages.add(new FieldActionMessage(
                    action.blocking() ? FieldActionMessage.Level.ERROR : FieldActionMessage.Level.WARNING,
                    message(action, request, interpolation),
                    request.fieldName(),
                    action.id(),
                    action.nodeType()));
            if (action.blocking()) {
                return new Outcome(true, messages);
            }
        }
        return new Outcome(false, messages);
    }

    /** The action's answer for this value, from the cache when it has one, executed and cached otherwise. */
    private FieldActionResult verdict(HttpServletRequest req, HttpServletResponse resp, FieldActionRequest request,
                                      ResolvedFieldAction action) {
        Duration ttl = cacheTtl.get();
        Optional<FieldActionResult> cached = cache.get(action.id(), request.value(), ttl);
        if (cached.isPresent()) {
            return cached.get();
        }
        FieldActionResult result = execute(req, resp, request, action);
        cache.put(action.id(), request.value(), result, ttl);
        return result;
    }

    private FieldActionResult execute(HttpServletRequest req, HttpServletResponse resp, FieldActionRequest request,
                                      ResolvedFieldAction action) {
        Optional<FieldAction> javaAction = javaActions.get().stream()
                .filter(candidate -> action.nodeType().equals(candidate.getNodeType()))
                .findFirst();
        try {
            FieldActionResult result = inLive(request.locale(), session -> {
                JCRNodeWrapper node = session.getNodeByIdentifier(action.id());
                if (javaAction.isPresent()) {
                    FieldActionResult answer = javaAction.get().execute(node, request);
                    return answer != null ? answer : FieldActionResult.unavailable("the Java action answered null");
                }
                String output;
                try {
                    output = viewRenderer.render(node, request, req, resp);
                } catch (Exception e) {
                    // a JCR callback may only throw RepositoryException: carry the render failure out to the catch below
                    throw new ViewFailure(e);
                }
                return output == null
                        ? FieldActionResult.unavailable("no request to render the view in")
                        : parse(output);
            });
            return result != null ? result : FieldActionResult.unavailable("no answer");
        } catch (Exception e) {
            // one action's failure is that action's verdict, never the visitor's stack trace nor the next action's fate
            log.warn("[FieldActionDispatcher] Field action {} ({}) failed on field '{}' of form {}",
                    action.id(), action.nodeType(), request.fieldName(), request.formId(), e);
            return FieldActionResult.unavailable(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * The verdict a {@code hidden.execute} view wrote: one JSON object, {@code {"verdict": "accept" | "reject" |
     * "unavailable", "detail"?: "…"}}, possibly wrapped in whitespace or markup the render chain added around it.
     * Anything else is unavailable, with what was read in the detail for the logs.
     */
    static FieldActionResult parse(String output) {
        String text = output == null ? "" : output.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return FieldActionResult.unavailable("the view answered no JSON object: " + abbreviate(text));
        }
        try {
            JSONObject json = new JSONObject(text.substring(start, end + 1));
            String verdict = json.optString("verdict", "").trim().toLowerCase(Locale.ROOT);
            String detail = json.has("detail") && !json.isNull("detail") ? json.optString("detail") : null;
            return switch (verdict) {
                case "accept" -> FieldActionResult.accept();
                case "reject" -> FieldActionResult.reject(detail);
                case "unavailable" -> FieldActionResult.unavailable(detail);
                default -> FieldActionResult.unavailable("the view answered the verdict '" + verdict + "'");
            };
        } catch (JSONException e) {
            return FieldActionResult.unavailable("the view answered malformed JSON: " + abbreviate(text));
        }
    }

    /**
     * The contributor's message for this action in the visitor's locale, {@code ${value}} and the other submitted
     * values interpolated and HTML-escaped; the rich text itself is the contributor's and trusted, as the form's
     * responses are. The bundle's default when the node carries none.
     */
    private String message(ResolvedFieldAction action, FieldActionRequest request, Map<String, List<String>> interpolation) {
        String template = null;
        try {
            template = inLive(request.locale(), session -> {
                JCRNodeWrapper node = session.getNodeByIdentifier(action.id());
                return node.hasProperty(FmdbProperty.REJECTION_MESSAGE) ? node.getProperty(FmdbProperty.REJECTION_MESSAGE).getString() : null;
            });
        } catch (RepositoryException | RuntimeException e) {
            log.warn("[FieldActionDispatcher] The rejection message of field action {} could not be read; the default is shown", action.id(), e);
        }
        if (template == null || template.isBlank()) {
            template = defaultMessage(request.locale());
        }
        Map<String, List<String>> values = new LinkedHashMap<>(interpolation == null ? Map.of() : interpolation);
        values.put(VALUE_PLACEHOLDER, List.of(request.value() == null ? "" : request.value()));
        return TemplateInterpolator.interpolate(template, values, FieldEscaper::html);
    }

    /**
     * The engine bundle's default message in the visitor's locale — read from the module's own resource bundle,
     * where the CND's default lives too — or the English literal when no bundle answers.
     */
    static String defaultMessage(Locale locale) {
        try {
            // no fallback to the server's own locale: an English visitor on a French server reads the base bundle, not the French one
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, locale == null ? Locale.ENGLISH : locale,
                    FieldActionDispatcher.class.getClassLoader(), ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
            String text = bundle.containsKey(DEFAULT_MESSAGE_KEY) ? bundle.getString(DEFAULT_MESSAGE_KEY) : null;
            return text == null || text.isBlank() ? DEFAULT_MESSAGE : text;
        } catch (MissingResourceException e) {
            return DEFAULT_MESSAGE;
        }
    }

    private static String abbreviate(String text) {
        return text.length() <= 120 ? text : text.substring(0, 120) + "…";
    }

    /** A view's failure carried out of a JCR callback, which may only throw RepositoryException. */
    private static final class ViewFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ViewFailure(Exception cause) {
            super(cause.getMessage(), cause);
        }
    }
}
