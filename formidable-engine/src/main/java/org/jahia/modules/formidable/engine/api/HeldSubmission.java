package org.jahia.modules.formidable.engine.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A submission that the pipeline accepted on a form that holds its uploads in quarantine
 * ({@code fmdbmix:quarantine}): what a {@link SubmissionHold} stores, and what it hands back to
 * {@link DeferredActions} when it releases the submission.
 *
 * <p>It holds values and no JCR node, so it outlives the request. The sensitive values wait with the others: the
 * actions that consume them run after release. A hold keeps them where no reader reaches them, returns them through
 * no read API, and deletes them with the other values once the submission is released or rejected.</p>
 *
 * @param formId              the identifier of the form, in the live workspace
 * @param siteKey             the key of the site of the form, or the empty string when the form could not say
 * @param locale              the locale of the submission
 * @param parameters          the accepted values of the fields that are not sensitive, by field name
 * @param sensitiveParameters the accepted values of the sensitive fields, by field name
 * @param files               the uploads: the ones to hold at intake, the released ones at release
 * @param origin              what the engine kept of the HTTP request
 */
public record HeldSubmission(
        String formId,
        String siteKey,
        Locale locale,
        Map<String, List<String>> parameters,
        Map<String, List<String>> sensitiveParameters,
        List<SubmittedFile> files,
        SubmissionOrigin origin
) {
    public HeldSubmission {
        Objects.requireNonNull(formId, "formId");
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(origin, "origin");
        siteKey = siteKey == null ? "" : siteKey;
        parameters = copyOf(parameters);
        sensitiveParameters = copyOf(sensitiveParameters);
        files = files == null ? List.of() : List.copyOf(files);
    }

    /** A copy that nobody changes, in the order of the source: an action lists the fields in that order. */
    private static Map<String, List<String>> copyOf(Map<String, List<String>> source) {
        if (source == null) {
            return Map.of();
        }
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((field, values) -> copy.put(field, List.copyOf(values)));
        return Collections.unmodifiableMap(copy);
    }

    /** What a log line may say: which fields, never what they hold. */
    @Override
    public String toString() {
        return "HeldSubmission[form=" + formId
                + ", fields=" + parameters.keySet()
                + ", sensitiveFields=" + sensitiveParameters.keySet()
                + ", files=" + files.size() + "]";
    }
}
