package org.jahia.modules.formidable.engine.api;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Everything a {@link ContextualFormAction} is handed for one submission.
 *
 * <p>Threading: built on the request thread, valid for the duration of the {@code execute} call only. The
 * JCR nodes belong to the system session of the call, which the pipeline closes when {@code execute} returns:
 * never keep one past it, never hand one to another thread.</p>
 *
 * <p>The values are split in two on purpose. {@link #parameters()} holds the fields every action may read;
 * {@link #sensitiveParameters()} holds the values of the fields carrying {@code fmdbmix:sensitiveField} (a
 * password) and is empty unless the action declared {@link ContextualFormAction#consumesSensitiveValues()}.
 * A sensitive value is therefore never in the map the built-in actions (save to JCR, e-mail, forward) read,
 * nor in what the response enrichers see.</p>
 *
 * @param actionNode          the action's configuration node, read in the live workspace
 * @param formNode            the submitted form, in the same session as {@code actionNode}
 * @param request             the submission request. {@code getRemoteAddr()} and the headers are the caller's
 * @param response            the submission response, for what an action may legitimately add to it: a cookie, a header.
 *                            An action never writes its body nor commits it: the servlet answers JSON once every action ran
 * @param session             the visitor's live session, as the plain {@link FormAction} receives it
 * @param locale              the submission's locale (the {@code lang} parameter), the one to translate a message in
 * @param siteKey             the key of the form's site
 * @param parameters          the accepted values of the non-sensitive fields, by field name
 * @param sensitiveParameters the accepted values of the sensitive fields, by field name: empty unless the action
 *                            consumes them
 * @param files               the validated uploads
 */
public record FormActionContext(
        JCRNodeWrapper actionNode,
        JCRNodeWrapper formNode,
        HttpServletRequest request,
        HttpServletResponse response,
        JCRSessionWrapper session,
        Locale locale,
        String siteKey,
        Map<String, List<String>> parameters,
        Map<String, List<String>> sensitiveParameters,
        List<SubmittedFile> files
) {
    public FormActionContext {
        Objects.requireNonNull(actionNode, "actionNode");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(locale, "locale");
        parameters = copyOf(parameters);
        sensitiveParameters = copyOf(sensitiveParameters);
        files = files == null ? List.of() : List.copyOf(files);
    }

    /** The first non-blank value of a non-sensitive field, or {@code null} when it was left unanswered. */
    public String parameter(String fieldName) {
        return first(parameters.get(fieldName));
    }

    /** The first non-blank value of a sensitive field, or {@code null}: unanswered, or not consumed by this action. */
    public String sensitiveParameter(String fieldName) {
        return first(sensitiveParameters.get(fieldName));
    }

    private static String first(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream().filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
    }

    private static Map<String, List<String>> copyOf(Map<String, List<String>> source) {
        return source == null ? Map.of() : source.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }

    /** What a log line may say: which fields, never what they hold. */
    @Override
    public String toString() {
        return "FormActionContext[action=" + actionNode.getPath()
                + ", fields=" + parameters.keySet()
                + ", sensitiveFields=" + sensitiveParameters.keySet()
                + ", files=" + files.size() + "]";
    }
}
