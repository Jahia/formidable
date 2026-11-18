package org.jahia.modules.formidable.engine.api;

/**
 * What the engine keeps of the HTTP request of a submission, for the actions that run after the release of a held
 * submission, when the request is gone. This is the whole list: an action that runs after release reads these three
 * values and nothing else of the request ({@link DetachedRequestException}).
 *
 * <p>The values are the client's own, kept as they were sent: the action that reads one validates it, as it does in
 * a request.</p>
 *
 * @param language the {@code lang} parameter of the submission URL, or {@code null}
 * @param referer  the {@code Referer} header, or {@code null}
 * @param timeZone the {@value #TIME_ZONE_HEADER} header, the time zone the browser reports, or {@code null}
 */
public record SubmissionOrigin(String language, String referer, String timeZone) {

    /** The parameter of the submission URL that carries the language. */
    public static final String LANGUAGE_PARAMETER = "lang";
    /** The header that carries the page the form was submitted from. */
    public static final String REFERER_HEADER = "Referer";
    /** The header that carries the time zone of the submitter, as the form client sends it. */
    public static final String TIME_ZONE_HEADER = "X-Formidable-Time-Zone";
}
