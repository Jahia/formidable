package org.jahia.modules.formidable.accounts.activation;

/**
 * Composes the activation e-mail and its link. Pure functions, thread-safe.
 *
 * @param subject the subject, one line
 * @param text    the plain-text body
 * @param html    the HTML body: the text escaped, the link made clickable
 */
public record ActivationMail(String subject, String text, String html) {

    /** Path of the verification servlet, under the platform's {@code /modules}. */
    static final String VERIFY_PATH = "/modules/formidable-accounts/verify";

    /**
     * The link the visitor follows, on the operator's configured origin only: never on the host a request named, which
     * the visitor controls.
     *
     * @param base            {@code publicBaseUrl} of the configuration, an http or https origin without a trailing slash
     * @param platformContext the platform's context path ({@code Jahia.getContextPath()}), empty for the root
     * @param token           the activation token
     * @return an absolute URL
     * @throws IllegalArgumentException when {@code base} is blank: the caller refuses the sign-up before it gets here
     */
    public static String link(String base, String platformContext, ActivationToken token) {
        if (base == null || base.isBlank()) {
            throw new IllegalArgumentException("no public base URL for the activation link");
        }
        return base + (platformContext == null ? "" : platformContext) + VERIFY_PATH + "?t=" + token.asParameter();
    }

    /**
     * @param subjectTemplate the subject, {@code ${link}} not replaced in it; one line
     * @param bodyTemplate    the body with {@code ${link}} and {@code ${hours}}
     * @param link            the verification link
     * @param hours           how long the link is valid
     * @return the mail
     */
    public static ActivationMail compose(String subjectTemplate, String bodyTemplate, String link, long hours) {
        String text = bodyTemplate.replace("${link}", link).replace("${hours}", Long.toString(hours));
        String escaped = escape(text).replace(escape(link), "<a href=\"" + escape(link) + "\">" + escape(link) + "</a>");
        String html = "<p>" + escaped.replace("\r\n", "\n").replace("\n\n", "</p><p>").replace("\n", "<br>") + "</p>";
        return new ActivationMail(oneLine(subjectTemplate), text, html);
    }

    static String oneLine(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
    }

    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** Never prints the body, which carries the link. */
    @Override
    public String toString() {
        return "ActivationMail[subject=" + subject + "]";
    }
}
