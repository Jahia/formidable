package org.jahia.modules.formidable.formsimport.jahiaforms;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.engine.api.FmdbNodeType;
import org.jahia.modules.formidable.engine.api.FmdbProperty;
import org.jahia.modules.formidable.formsimport.model.ImportNote;
import org.jahia.modules.formidable.formsimport.model.ImportedAction;
import org.jahia.modules.formidable.formsimport.model.ImportedForm;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.jahia.modules.formidable.formsimport.jahiaforms.FormsFormConverterTest.has;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormsRedirectsTest {

    private static final String TARGET = "target";
    private static final String HOME = "/sites/motor-retail/home";
    private static final String HOME_ID = "uuid-home";
    private static final String THANKS = "/sites/motor-retail/thanks";
    private static final String THANKS_ID = "uuid-thanks";

    /** A site holding two pages, where the form is displayed on the pages given. */
    private static ImportEnvironment.SitePages site(FormsDisplay... displays) {
        return new ImportEnvironment.SitePages() {
            @Override
            public Optional<String> pageOf(String reference) {
                return switch (reference) {
                    case HOME -> Optional.of(HOME_ID);
                    case THANKS -> Optional.of(THANKS_ID);
                    default -> Optional.empty();
                };
            }

            @Override
            public List<FormsDisplay> displaysOf(String formUuid) {
                return List.of(displays);
            }
        };
    }

    private static ImportedForm convert(ImportEnvironment.SitePages pages, FormsAction... actions) {
        FormsForm form = new FormsForm("f", "uf", "formFactory/forms/f", "en", Map.of("en", "F", "fr", "F"), Map.of(),
                FormsForm.Settings.NONE, List.of(new FormsStep("step-1", 1, Map.of(), List.of())), List.of(actions));
        return new FormsFormConverter(new ImportEnvironment(type -> true, s -> true, type -> false, ImportEnvironment.ANY_PROPERTY, false, pages))
                .convert(form, null);
    }

    private static FormsAction toPage(String name, Map<String, String> byLanguage) {
        return new FormsAction(name, FormsAction.REDIRECT_TO_PAGE,
                Map.of(FormsOptionNames.REDIRECT_TO, new FormsOption(FormsOptionNames.REDIRECT_TO, null, byLanguage)));
    }

    private static FormsAction toUrl(String name, Map<String, String> byLanguage) {
        return new FormsAction(name, FormsAction.REDIRECT_TO_URL,
                Map.of(FormsOptionNames.REDIRECT_TO, new FormsOption(FormsOptionNames.REDIRECT_TO, null, byLanguage)));
    }

    private static List<ImportedAction> redirects(ImportedForm form) {
        return form.actions().stream().filter(action -> FmdbNodeType.REDIRECT_ACTION.equals(action.nodeType())).toList();
    }

    @Test
    void theSampleRedirectToAPageBecomesARedirectToThatPageOfTheSite() throws Exception {
        FormsExport export = FormsExportReaderTest.sampleReader().readStructure();
        FormsForm source = export.forms().get("contact-us");

        ImportedForm form = new FormsFormConverter(new ImportEnvironment(type -> true, s -> true, type -> false,
                ImportEnvironment.ANY_PROPERTY, false, site())).convert(source, export.resultsOf(source));

        assertEquals(List.of(FmdbNodeType.SAVE_TO_JCR_ACTION, FmdbNodeType.REDIRECT_ACTION),
                form.actions().stream().map(ImportedAction::nodeType).toList());
        ImportedAction redirect = form.actions().get(1);
        assertEquals(Map.of(FmdbProperty.REDIRECT_TARGET_TYPE, "page", FmdbProperty.REDIRECT_PAGE, HOME_ID), redirect.properties());
        // the target's mixin, without which the repository has no definition for the page property
        assertEquals(List.of(FmdbMixin.REDIRECT_TO_PAGE), redirect.mixins());
        assertTrue(has(form.report(), "form.redirectPage", TARGET, HOME), form.report().toString());
    }

    @Test
    void aPageTheSiteDoesNotHoldIsReportedAndNoRedirectIsCreated() {
        ImportedForm form = convert(ImportEnvironment.NO_PAGES, toPage("redirecttoapage", Map.of("en", HOME)));

        assertTrue(redirects(form).isEmpty());
        assertTrue(form.report().stream().anyMatch(note -> note.code().equals("form.redirectPageNotFound")
                && note.level() == ImportNote.Level.ACTION && HOME.equals(note.param(TARGET))), form.report().toString());
    }

    @Test
    void aPagePerLanguageKeepsThePageOfTheBuildingLanguageForAll() {
        ImportedForm form = convert(site(), toPage("redirecttoapage", Map.of("en", HOME, "fr", THANKS)));

        assertEquals(HOME_ID, redirects(form).get(0).properties().get(FmdbProperty.REDIRECT_PAGE));
        assertTrue(has(form.report(), "form.redirectPageLanguages", TARGET, HOME), form.report().toString());
    }

    @Test
    void aUrlKeepsItsValuePerLanguage() {
        ImportedForm form = convert(site(), toUrl("redirecttourl", Map.of("en", "https://www.example.com/thanks", "fr", "https://www.example.com/merci")));

        ImportedAction redirect = redirects(form).get(0);
        assertEquals(Map.of(FmdbProperty.REDIRECT_TARGET_TYPE, "url"), redirect.properties());
        assertEquals(List.of(FmdbMixin.REDIRECT_TO_URL), redirect.mixins());
        assertEquals(Map.of("en", "https://www.example.com/thanks", "fr", "https://www.example.com/merci"),
                redirect.i18nProperties().get(FmdbProperty.REDIRECT_URL));
        assertTrue(form.report().stream().anyMatch(note -> note.code().equals("form.redirectUrl")), form.report().toString());
    }

    @Test
    void aUrlWithoutSchemeTakesTheHttpForTheFormsAddedAndSaysSo() {
        ImportedForm form = convert(site(), toUrl("redirecttourl", Map.of("en", "www.example.com/thanks")));

        assertEquals(Map.of("en", "http://www.example.com/thanks"), redirects(form).get(0).i18nProperties().get(FmdbProperty.REDIRECT_URL));
        assertTrue(has(form.report(), "form.redirectUrlCompleted", TARGET, "http://www.example.com/thanks"), form.report().toString());
        // a path of the site too, as Forms did: http:///path
        assertEquals("http:///sites/motor-retail/thanks.html", FormsRedirects.followedUrl("/sites/motor-retail/thanks.html"));
        assertEquals("https://a.example", FormsRedirects.followedUrl(" https://a.example "));
    }

    @Test
    void aPathInARedirectToUrlIsAUrlAsFormsReadItAndIsReported() {
        // Verifies that the action type decides the target: a redirect-to-URL action holds a URL, so a path of the site
        // takes Forms' http:// (http:///path, no host) and is reported, never turned into a page target.
        ImportedForm form = convert(site(), toUrl("redirecttourl", Map.of("en", THANKS + ".html")));

        assertTrue(redirects(form).isEmpty());
        assertTrue(has(form.report(), "form.redirectUrlRefused", TARGET, THANKS + ".html"), form.report().toString());
    }

    @Test
    void aUrlFormidableCannotSendAVisitorToIsReported() {
        ImportedForm form = convert(site(), toUrl("redirecttourl", Map.of("en", "not an address", "fr", "https://www.example.com/merci")));

        // the language whose URL is usable keeps it, the other is reported
        assertEquals(Map.of("fr", "https://www.example.com/merci"), redirects(form).get(0).i18nProperties().get(FmdbProperty.REDIRECT_URL));
        assertTrue(has(form.report(), "form.redirectUrlRefused", TARGET, "not an address"), form.report().toString());
        ImportedForm none = convert(site(), toUrl("redirecttourl", Map.of("en", "not an address")));
        assertTrue(redirects(none).isEmpty());
    }

    @Test
    void aRedirectWithoutTargetIsReported() {
        ImportedForm form = convert(site(), toUrl("redirecttourl", Map.of("en", " ")));

        assertTrue(redirects(form).isEmpty());
        assertTrue(has(form.report(), "form.redirectNoTarget"), form.report().toString());
    }

    @Test
    void theRedirectFormsFollowedIsTheOnlyOneCarriedOverTheOthersAreReported() {
        ImportedForm form = convert(site(), toPage("redirecttoapage", Map.of("en", HOME)), toUrl("redirecttourl", Map.of("en", "https://a.example")));

        assertEquals(1, redirects(form).size());
        assertEquals("url", redirects(form).get(0).properties().get(FmdbProperty.REDIRECT_TARGET_TYPE));
        assertTrue(has(form.report(), "form.redirectIgnored", TARGET, HOME), form.report().toString());
    }

    @Test
    void formsKeepsThePlaceOfTheFirstActionOfAKindAndTheTargetOfTheLast() {
        // Forms answers one entry per action name, in the place of the first: url (holding B), then page; the page is last
        ImportedForm form = convert(site(),
                toUrl("redirecttourl", Map.of("en", "https://a.example")),
                toPage("redirecttoapage", Map.of("en", THANKS)),
                toUrl("redirecttourl_1", Map.of("en", "https://b.example")));

        assertEquals(List.of(THANKS_ID), redirects(form).stream().map(a -> a.properties().get(FmdbProperty.REDIRECT_PAGE)).toList());
        assertTrue(has(form.report(), "form.redirectIgnored", TARGET, "https://a.example"), form.report().toString());
        assertTrue(has(form.report(), "form.redirectIgnored", TARGET, "https://b.example"), form.report().toString());
    }

    @Test
    void theRedirectUrlOfTheDisplayTakesThePlaceOfTheRedirectToUrlAction() {
        ImportedForm form = convert(site(new FormsDisplay("/sites/motor-retail/contact", "https://d.example/thanks")),
                toUrl("redirecttourl", Map.of("en", "https://a.example")), toPage("redirecttoapage", Map.of("en", HOME)));

        // the display took the place of the url entry, before the page: the page is followed
        assertEquals(HOME_ID, redirects(form).get(0).properties().get(FmdbProperty.REDIRECT_PAGE));
        assertTrue(has(form.report(), "form.redirectIgnored", TARGET, "https://a.example"), form.report().toString());

        ImportedForm displayOnly = convert(site(new FormsDisplay("/sites/motor-retail/contact", "https://d.example/thanks")),
                toPage("redirecttoapage", Map.of("en", HOME)));
        // added last, the URL of the display is followed, in every language of the form
        assertEquals(Map.of("en", "https://d.example/thanks", "fr", "https://d.example/thanks"),
                redirects(displayOnly).get(0).i18nProperties().get(FmdbProperty.REDIRECT_URL));
        assertTrue(has(displayOnly.report(), "form.redirectDisplay", TARGET, "https://d.example/thanks", "page", "/sites/motor-retail/contact"),
                displayOnly.report().toString());
        assertTrue(has(displayOnly.report(), "form.redirectIgnored", TARGET, HOME), displayOnly.report().toString());
    }

    @Test
    void displaysThatSentTheVisitorsElsewhereAreReportedPageByPage() {
        ImportedForm form = convert(site(new FormsDisplay("/sites/motor-retail/a", "https://d.example/thanks"),
                        new FormsDisplay("/sites/motor-retail/b", null)),
                toPage("redirecttoapage", Map.of("en", HOME)));

        // the first display decides; the second followed the page, reported with its page, and not as ignored
        assertEquals("url", redirects(form).get(0).properties().get(FmdbProperty.REDIRECT_TARGET_TYPE));
        assertTrue(has(form.report(), "form.redirectOtherPage", "page", "/sites/motor-retail/b", TARGET, HOME), form.report().toString());
        assertFalse(has(form.report(), "form.redirectIgnored"), form.report().toString());
    }

    @Test
    void aFormWithoutRedirectGetsNone() {
        ImportedForm form = convert(site(new FormsDisplay("/sites/motor-retail/a", null)));

        assertTrue(redirects(form).isEmpty());
        assertTrue(form.report().stream().noneMatch(note -> note.code().startsWith("form.redirect")), form.report().toString());
    }
}
