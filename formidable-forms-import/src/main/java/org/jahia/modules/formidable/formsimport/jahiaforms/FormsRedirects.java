package org.jahia.modules.formidable.formsimport.jahiaforms;

import org.jahia.modules.formidable.engine.api.FmdbNodeType;
import org.jahia.modules.formidable.engine.api.FmdbProperty;
import org.jahia.modules.formidable.engine.api.RedirectTargets;
import org.jahia.modules.formidable.formsimport.model.ImportNote;
import org.jahia.modules.formidable.formsimport.model.ImportedAction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The one redirect a Forms form becomes (docs/architecture/forms-import.md, "The redirects"): the one Forms followed,
 * so that a migrated form keeps its target. Formidable follows the first redirect of a form, Forms its last, so the
 * import never carries more than one; the others are reported.
 *
 * <p>Which one Forms followed comes from how it answers a submission ({@code FormSubmission.executeActions}): one
 * entry per action <em>name</em>, {@code redirectToAPage} or {@code redirectToUrl}, in a map that keeps the place of
 * the first action of a name and the result of the last; the {@code redirectUrl} of the display that showed the form
 * takes the {@code redirectToUrl} entry, or is added last; the browser follows the last entry
 * ({@code handleRedirectIfPresent}).</p>
 */
final class FormsRedirects {

    static final String ACTION_NAME = "redirect";
    private static final String TARGET = "target";
    private static final String PAGE = "page";
    private static final String TO_PAGE = "redirectToAPage";
    private static final String TO_URL = "redirectToUrl";
    private static final String HTTP = "http://";
    private static final String HTTPS = "https://";

    /** A redirect Forms could follow: one of its actions, or the URL of the display that showed the form. */
    private record Candidate(FormsAction action, String displayUrl) {

        boolean isPage() {
            return action != null && action.is(FormsAction.REDIRECT_TO_PAGE);
        }
    }

    private FormsRedirects() {
    }

    /**
     * @param form         the Forms form
     * @param buildingLang the language the form was built in, whose value names a page shared by every language
     * @param languages    the languages of the form, which a value given once, for every language, is written in
     * @param pages        the pages of the site and the displays of the form
     * @param report       the notes of the form, which the reported redirects are added to
     * @return the redirect action, when Forms followed a redirect the import can carry
     */
    static Optional<ImportedAction> convert(FormsForm form, String buildingLang, Collection<String> languages,
                                            ImportEnvironment.SitePages pages, List<ImportNote> report) {
        List<FormsAction> redirects = form.actions().stream()
                .filter(action -> action.is(FormsAction.REDIRECT_TO_PAGE) || action.is(FormsAction.REDIRECT_TO_URL))
                .toList();
        List<FormsDisplay> displays = form.uuid() == null ? List.of() : pages.displaysOf(form.uuid());
        Set<Candidate> followed = new HashSet<>();
        Candidate chosen = chosen(redirects, displays, buildingLang, followed, report);
        if (chosen == null) {
            return Optional.empty();
        }
        followed.add(chosen);
        reportIgnored(redirects, followed, buildingLang, report);
        if (chosen.displayUrl() != null) {
            return urlAction(spread(chosen.displayUrl(), languages), report, displays.get(0).page());
        }
        FormsOption target = chosen.action().option(FormsOptionNames.REDIRECT_TO);
        if (target == null || target.isBlank()) {
            report.add(ImportNote.action("form.redirectNoTarget"));
            return Optional.empty();
        }
        return chosen.isPage()
                ? pageAction(target, buildingLang, pages, report)
                : urlAction(byLanguage(target, languages), report, null);
    }

    /**
     * The redirect the form keeps: the one Forms followed from its first display, or with no display. A Formidable form
     * redirects the same way on every page, so a display that led elsewhere is reported, its redirect added to
     * {@code followed}.
     */
    private static Candidate chosen(List<FormsAction> redirects, List<FormsDisplay> displays, String buildingLang,
                                    Set<Candidate> followed, List<ImportNote> report) {
        if (displays.isEmpty()) {
            return followed(redirects, null);
        }
        Candidate chosen = followed(redirects, urlOf(displays.get(0)));
        for (FormsDisplay display : displays.subList(1, displays.size())) {
            Candidate other = followed(redirects, urlOf(display));
            if (other != null && !other.equals(chosen)) {
                report.add(ImportNote.action("form.redirectOtherPage", PAGE, display.page(), TARGET, targetOf(other, buildingLang)));
                followed.add(other);
            }
        }
        return chosen;
    }

    /** The redirect actions Forms never followed, reported. */
    private static void reportIgnored(List<FormsAction> redirects, Set<Candidate> followed, String buildingLang,
                                      List<ImportNote> report) {
        for (FormsAction redirect : redirects) {
            Candidate candidate = new Candidate(redirect, null);
            if (!followed.contains(candidate)) {
                report.add(ImportNote.info("form.redirectIgnored", TARGET, targetOf(candidate, buildingLang)));
            }
        }
    }

    /** The redirect Forms followed after a submission from a display with that URL, or with none; null when none. */
    private static Candidate followed(List<FormsAction> redirects, String displayUrl) {
        List<String> order = new ArrayList<>();
        Map<String, Candidate> last = new LinkedHashMap<>();
        for (FormsAction redirect : redirects) {
            String name = redirect.is(FormsAction.REDIRECT_TO_PAGE) ? TO_PAGE : TO_URL;
            if (!order.contains(name)) {
                order.add(name);
            }
            last.put(name, new Candidate(redirect, null));
        }
        if (displayUrl != null) {
            if (!order.contains(TO_URL)) {
                order.add(TO_URL);
            }
            last.put(TO_URL, new Candidate(null, displayUrl));
        }
        return order.isEmpty() ? null : last.get(order.get(order.size() - 1));
    }

    private static String urlOf(FormsDisplay display) {
        return display.redirectUrl() == null || display.redirectUrl().isBlank() ? null : display.redirectUrl().trim();
    }

    /** What a note names of a redirect: the page or the URL, as Forms stored it. */
    private static String targetOf(Candidate candidate, String buildingLang) {
        if (candidate.displayUrl() != null) {
            return candidate.displayUrl();
        }
        FormsOption target = candidate.action().option(FormsOptionNames.REDIRECT_TO);
        return target == null ? "" : valueIn(target, buildingLang);
    }

    /** The value of the option in the language, else its first non-blank value, trimmed; empty when it has none. */
    private static String valueIn(FormsOption option, String language) {
        String value = option.in(language);
        if (value == null || value.isBlank()) {
            value = option.values().values().stream().filter(v -> v != null && !v.isBlank()).findFirst().orElse(option.value());
        }
        return value == null ? "" : value.trim();
    }

    /**
     * A page is the same node in every language: the one of the building language is kept, and a form that sent
     * each language to its own page is reported.
     */
    private static Optional<ImportedAction> pageAction(FormsOption target, String buildingLang, ImportEnvironment.SitePages pages,
                                                       List<ImportNote> report) {
        String reference = valueIn(target, buildingLang);
        Optional<String> page = pages.pageOf(reference);
        if (page.isEmpty()) {
            report.add(ImportNote.action("form.redirectPageNotFound", TARGET, reference));
            return Optional.empty();
        }
        boolean several = target.values().values().stream()
                .anyMatch(value -> value != null && !value.isBlank() && !value.trim().equals(reference));
        report.add(several
                ? ImportNote.action("form.redirectPageLanguages", TARGET, reference)
                : ImportNote.info("form.redirectPage", TARGET, reference));
        return Optional.of(new ImportedAction(ACTION_NAME, FmdbNodeType.REDIRECT_ACTION,
                Map.of(FmdbProperty.REDIRECT_TARGET_TYPE, "page", FmdbProperty.REDIRECT_PAGE, page.get()), Map.of()));
    }

    /**
     * Each language keeps the URL Forms sent it to, when Formidable can send a visitor there: a path of this server
     * ({@link RedirectTargets#sameOriginPath}) or an absolute http(s) URL ({@link RedirectTargets#httpUrl}); the others
     * are reported.
     *
     * @param display the page of the display the URL comes from, null for the URL of an action
     */
    private static Optional<ImportedAction> urlAction(Map<String, String> stored, List<ImportNote> report, String display) {
        Map<String, String> urls = new LinkedHashMap<>();
        Set<String> refused = new HashSet<>();
        Set<String> completed = new HashSet<>();
        for (Map.Entry<String, String> value : stored.entrySet()) {
            String followed = followedUrl(value.getValue());
            Optional<String> url = RedirectTargets.sameOriginPath(followed).or(() -> RedirectTargets.httpUrl(followed));
            if (url.isEmpty()) {
                if (refused.add(value.getValue().trim())) {
                    report.add(ImportNote.action("form.redirectUrlRefused", TARGET, value.getValue().trim()));
                }
            } else {
                urls.put(value.getKey(), url.get());
                if (!followed.equals(value.getValue().trim()) && completed.add(url.get())) {
                    report.add(ImportNote.action("form.redirectUrlCompleted", TARGET, url.get()));
                }
            }
        }
        if (urls.isEmpty()) {
            if (stored.isEmpty()) {
                report.add(ImportNote.action("form.redirectNoTarget"));
            }
            return Optional.empty();
        }
        String shown = urls.values().iterator().next();
        report.add(display == null
                ? ImportNote.info("form.redirectUrl", TARGET, shown)
                : ImportNote.info("form.redirectDisplay", TARGET, shown, PAGE, display));
        return Optional.of(new ImportedAction(ACTION_NAME, FmdbNodeType.REDIRECT_ACTION,
                Map.of(FmdbProperty.REDIRECT_TARGET_TYPE, "url"), Map.of(FmdbProperty.REDIRECT_URL, urls)));
    }

    /**
     * The URL Forms sent the visitor to: Forms adds {@code http://} to a value that starts with neither
     * {@code http://} nor {@code https://}. A path of the site is kept as it is, which Forms broke into
     * {@code http:///path}.
     */
    static String followedUrl(String stored) {
        String value = stored == null ? "" : stored.trim();
        if (value.isEmpty() || value.startsWith(HTTP) || value.startsWith(HTTPS) || value.startsWith("/")) {
            return value;
        }
        return HTTP + value;
    }

    /** The non-blank values of the option by language; a value given once is written in every language of the form. */
    private static Map<String, String> byLanguage(FormsOption option, Collection<String> languages) {
        if (option.values().isEmpty()) {
            return spread(option.value(), languages);
        }
        Map<String, String> values = new LinkedHashMap<>();
        option.values().forEach((language, value) -> {
            if (value != null && !value.isBlank()) {
                values.put(language, value);
            }
        });
        return values;
    }

    private static Map<String, String> spread(String value, Collection<String> languages) {
        Map<String, String> values = new LinkedHashMap<>();
        if (value != null && !value.isBlank()) {
            languages.forEach(language -> values.put(language, value));
        }
        return values;
    }
}
