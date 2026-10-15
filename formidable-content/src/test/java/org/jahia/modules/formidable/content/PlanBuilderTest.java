package org.jahia.modules.formidable.content;

import org.jahia.modules.formidable.content.convert.Reference;
import org.jahia.modules.formidable.content.convert.TypedValue;
import org.jahia.modules.formidable.content.mapping.ConfigException;
import org.jahia.modules.formidable.content.mapping.Mappings;
import org.jahia.modules.formidable.content.mapping.PairRule;
import org.jahia.modules.formidable.content.plan.ContentPlan;
import org.jahia.modules.formidable.content.plan.PlanBuilder;
import org.jahia.modules.formidable.content.plan.Settings;
import org.jahia.modules.formidable.content.plan.ValueException;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanBuilderTest {

    private static final Fakes.Catalog CATALOG = new Fakes.Catalog();
    private static final PlanBuilder.Target TARGET = new PlanBuilder.Target("/sites/orvane/contents/briefs/northlight", "/sites/orvane/files/briefs/northlight");
    private static final PlanBuilder.Identity WHO = new PlanBuilder.Identity("ada@northlight.example", "Northlight Studio");
    private static final List<Locale> LANGUAGES = List.of(Locale.ENGLISH, Locale.FRENCH);

    /** The settings of the Orvane supplier form, adjusted by the test. */
    private static final class S {
        String type = Fakes.Catalog.CONTENT_TYPE;
        String titleField = "title";
        List<String> fields = new ArrayList<>(List.of("summary=description", "minutes=durationMinutes", "budget=budget",
                "agreed=approved", "due=deadline", "channels=channels"));
        List<String> fileMappings = new ArrayList<>(List.of("media=media", "captions=captions"));
        String fileMixin = Fakes.Catalog.FILE_MIXIN;
        List<String> fileProperties = new ArrayList<>(List.of("credit=ctmdCredit"));
        List<String> fixed = new ArrayList<>(List.of("submissionStatus=submitted"));
        String submitter = "submittedBy";
        String organization = "company";
        String page = "brief";
        List<String> pairs = new ArrayList<>(List.of("captions<-media:video/*"));
        boolean publish = true;

        Settings build() throws ConfigException {
            return new Settings(type, "orvane-supplier-", titleField, Mappings.parse("fieldMappings", fields),
                    Mappings.parse("fileMappings", fileMappings), fileMixin, Mappings.parse("fileProperties", fileProperties),
                    Mappings.parse("fixedValues", fixed), submitter, organization, page, PairRule.parse(pairs), publish);
        }
    }

    private static SubmittedFile file(String field, String name, String mime) {
        return new SubmittedFile(field, name, mime, new byte[]{1, 2, 3});
    }

    private static Map<String, List<String>> values() {
        Map<String, List<String>> values = new HashMap<>();
        values.put("title", List.of("Summer launch film"));
        values.put("summary", List.of("A 30 second film"));
        values.put("minutes", List.of("2"));
        values.put("budget", List.of("1250.5"));
        values.put("agreed", List.of("true"));
        values.put("due", List.of("2026-11-01"));
        values.put("channels", List.of("web", "social"));
        values.put("credit", List.of("Northlight Studio"));
        return values;
    }

    private static List<SubmittedFile> films() {
        return List.of(file("media", "film.mp4", "video/mp4"), file("captions", "film.vtt", "text/vtt"));
    }

    private static ContentPlan build(S s, Map<String, List<String>> values, List<SubmittedFile> files, String pageId)
            throws ConfigException, ValueException {
        return PlanBuilder.build(s.build(), new PlanBuilder.Submission(values, files), WHO, pageId, TARGET, LANGUAGES, CATALOG);
    }

    private static TypedValue property(ContentPlan plan, String name) {
        return plan.properties().stream().filter(value -> value.shape().name().equals(name)).findFirst().orElse(null);
    }

    @Test
    void theWholeSubmissionBecomesOnePlan() throws Exception {
        ContentPlan plan = build(new S(), values(), films(), "page-uuid");

        assertEquals("ctmd:submission", plan.contentType());
        assertEquals(TARGET.parentPath(), plan.parentPath());
        assertEquals(TARGET.filesPath(), plan.filesPath());
        assertEquals("Summer launch film", plan.title());
        assertEquals(List.of("A 30 second film"), property(plan, "description").values());
        assertEquals(List.of(2L), property(plan, "durationMinutes").values());
        assertEquals(List.of(1250.5), property(plan, "budget").values());
        assertEquals(List.of(true), property(plan, "approved").values());
        assertEquals(List.of("web", "social"), property(plan, "channels").values());
        assertEquals(List.of("submitted"), property(plan, "submissionStatus").values());
        assertEquals(List.of("ada@northlight.example"), property(plan, "submittedBy").values());
        assertEquals(List.of("Northlight Studio"), property(plan, "company").values());
        assertEquals(List.of(new Reference("page-uuid")), property(plan, "brief").values());
        assertEquals(2, plan.fileGroups().size());
        assertEquals("media", plan.fileGroups().get(0).property().name());
        assertEquals("film.mp4", plan.fileGroups().get(0).files().get(0).originalName());
        assertEquals(Fakes.Catalog.FILE_MIXIN, plan.fileMixin());
        assertEquals(List.of("Northlight Studio"), plan.fileProperties().get(0).values());
        assertTrue(plan.publish());
        assertEquals(LANGUAGES, plan.languages());
    }

    @Test
    void theTitleIsNotAPropertyOfThePlanAndItsMappingIsNotRequired() throws Exception {
        ContentPlan plan = build(new S(), values(), films(), null);
        assertEquals("Summer launch film", plan.title());
        assertNull(property(plan, "jcr:title"));
    }

    @Test
    void noTitleFieldMeansNoTitle() throws Exception {
        S s = new S();
        s.titleField = null;
        assertNull(build(s, values(), films(), null).title());
        Map<String, List<String>> blank = values();
        blank.put("title", List.of("  "));
        assertNull(build(new S(), blank, films(), null).title());
    }

    @Test
    void aVeryLongTitleIsCut() throws Exception {
        Map<String, List<String>> long_ = values();
        long_.put("title", List.of("x".repeat(5000)));
        assertEquals(PlanBuilder.MAX_TITLE, build(new S(), long_, films(), null).title().length());
    }

    @Test
    void whatThePlatformKnowsOverridesWhatTheFormSaid() throws Exception {
        S s = new S();
        s.fields.add("rogue=submissionStatus");
        s.fields.add("rogueUser=submittedBy");
        Map<String, List<String>> values = values();
        values.put("rogue", List.of("approved"));
        values.put("rogueUser", List.of("someone-else@example.com"));
        ContentPlan plan = build(s, values, films(), null);
        assertEquals(List.of("submitted"), property(plan, "submissionStatus").values(), "the fixed value wins over a mapped field");
        assertEquals(List.of("ada@northlight.example"), property(plan, "submittedBy").values(), "the submitter wins over a mapped field");
    }

    @Test
    void aFieldLeftBlankIsNotWritten() throws Exception {
        Map<String, List<String>> values = values();
        values.remove("budget");
        values.put("due", List.of(""));
        ContentPlan plan = build(new S(), values, films(), null);
        assertNull(property(plan, "budget"));
        assertNull(property(plan, "deadline"));
    }

    @Test
    void theOrganizationAndThePageAreLeftOutWhenUnknown() throws Exception {
        PlanBuilder.Identity nobody = new PlanBuilder.Identity("ada@northlight.example", null);
        ContentPlan plan = PlanBuilder.build(new S().build(), new PlanBuilder.Submission(values(), films()), nobody, null, TARGET, LANGUAGES, CATALOG);
        assertNull(property(plan, "company"));
        assertNull(property(plan, "brief"));
        assertEquals(List.of("ada@northlight.example"), property(plan, "submittedBy").values());
    }

    @Test
    void aValueThatDoesNotFitIsAValueErrorThatNamesTheFieldButNotTheValue() throws Exception {
        Map<String, List<String>> values = values();
        values.put("minutes", List.of("two hours"));
        ValueException error = assertThrows(ValueException.class, () -> build(new S(), values, films(), null));
        assertEquals(ValueException.Kind.BAD_VALUE, error.kind());
        assertTrue(error.getMessage().contains("minutes"));
        assertFalse(error.getMessage().contains("two hours"));
    }

    @Test
    void aVideoWithoutCaptionsIsAPairErrorBeforeAnythingElseIsChecked() throws Exception {
        S s = new S();
        s.type = "nope:missing";
        ValueException error = assertThrows(ValueException.class,
                () -> build(s, values(), List.of(file("media", "film.mp4", "video/mp4")), null));
        assertEquals(ValueException.Kind.PAIR, error.kind());
        assertEquals("captions", error.rule().dependentField());
    }

    @Test
    void anImageNeedsNoCaptions() throws Exception {
        ContentPlan plan = build(new S(), values(), List.of(file("media", "cover.png", "image/png")), null);
        assertEquals(1, plan.fileGroups().size());
    }

    @Test
    void filesOfAFieldThatIsNotMappedAreNotStored() throws Exception {
        ContentPlan plan = build(new S(), values(), List.of(file("other", "x.pdf", "application/pdf")), null);
        assertTrue(plan.fileGroups().isEmpty());
    }

    @Test
    void severalFilesForASingleValuedPropertyAreAValueError() throws Exception {
        S s = new S();
        s.fileMappings.add("covers=cover");
        List<SubmittedFile> files = List.of(file("covers", "a.png", "image/png"), file("covers", "b.png", "image/png"));
        ValueException error = assertThrows(ValueException.class, () -> build(s, values(), files, null));
        assertEquals(ValueException.Kind.BAD_VALUE, error.kind());
        assertEquals(1, build(s, values(), List.of(file("covers", "a.png", "image/png")), null).fileGroups().size());
    }

    @Test
    void twoFieldsMappedToTheSameMultiplePropertyShareOneGroup() throws Exception {
        S s = new S();
        s.fileMappings = new ArrayList<>(List.of("photos=media", "videos=media"));
        s.pairs = new ArrayList<>();
        ContentPlan plan = build(s, values(), List.of(file("photos", "a.png", "image/png"), file("videos", "b.mp4", "video/mp4")), null);
        assertEquals(1, plan.fileGroups().size());
        assertEquals(2, plan.fileGroups().get(0).files().size());
    }

    @Test
    void anUnknownTypeIsAConfigurationError() {
        S s = new S();
        s.type = "ctmd:unknown";
        assertThrows(ConfigException.class, () -> build(s, values(), films(), null));
    }

    @Test
    void anUnknownPropertyIsAConfigurationErrorInEverySetting() {
        for (String setting : List.of("fields", "fixed", "fileMappings", "fileProperties")) {
            S s = new S();
            switch (setting) {
                case "fields" -> s.fields.add("summary=nosuch");
                case "fixed" -> s.fixed.add("nosuch=1");
                case "fileMappings" -> s.fileMappings.add("x=nosuch");
                default -> s.fileProperties.add("credit=nosuch");
            }
            ConfigException error = assertThrows(ConfigException.class, () -> build(s, values(), films(), null), setting);
            assertTrue(error.getMessage().contains("nosuch"), setting);
        }
        S s = new S();
        s.submitter = "nosuch";
        assertThrows(ConfigException.class, () -> build(s, values(), films(), null));
    }

    @Test
    void aReferencePropertyCannotBeFilledFromTextAndAFileMappingNeedsAReference() {
        S text = new S();
        text.fields.add("summary=brief");
        assertThrows(ConfigException.class, () -> build(text, values(), films(), null));
        S fixed = new S();
        fixed.fixed.add("brief=x");
        assertThrows(ConfigException.class, () -> build(fixed, values(), films(), null));
        S files = new S();
        files.fileMappings.add("media=description");
        assertThrows(ConfigException.class, () -> build(files, values(), films(), null));
    }

    @Test
    void aTypeThatTextCannotFillIsAConfigurationError() {
        S s = new S();
        s.fields.add("summary=score");
        assertThrows(ConfigException.class, () -> build(s, values(), films(), null));
    }

    @Test
    void aSingleValuedPropertyMappedFromTwoFieldsIsAConfigurationError() {
        S s = new S();
        s.fields.add("summary=durationMinutes");
        assertThrows(ConfigException.class, () -> build(s, values(), films(), null));
    }

    @Test
    void aBadFixedValueIsAConfigurationErrorNotTheVisitorsFault() {
        S s = new S();
        s.fixed.add("durationMinutes=many");
        assertThrows(ConfigException.class, () -> build(s, values(), films(), null));
    }

    @Test
    void theSubmitterAndPagePropertiesMustHaveTheRightShape() {
        S submitter = new S();
        submitter.submitter = "durationMinutes";
        assertThrows(ConfigException.class, () -> build(submitter, values(), films(), null));
        S page = new S();
        page.page = "media";
        assertThrows(ConfigException.class, () -> build(page, values(), films(), null), "a multiple reference is not a page");
        S text = new S();
        text.page = "description";
        assertThrows(ConfigException.class, () -> build(text, values(), films(), null));
    }

    @Test
    void theFileMixinMustBeAMixin() {
        S s = new S();
        s.fileMixin = "ctmd:submission";
        assertThrows(ConfigException.class, () -> build(s, values(), films(), null));
        S missing = new S();
        missing.fileMixin = "nope:mixin";
        assertThrows(ConfigException.class, () -> build(missing, values(), films(), null));
    }

    @Test
    void aTitleFieldNeedsATypeThatHasATitle() {
        Fakes.Catalog bare = new Fakes.Catalog() {
            @Override
            public java.util.Optional<org.jahia.modules.formidable.content.convert.PropertyShape> property(List<String> types, String name) {
                return name.equals("jcr:title") && types.contains(Fakes.Catalog.CONTENT_TYPE) ? java.util.Optional.empty() : super.property(types, name);
            }
        };
        S s = new S();
        assertThrows(ConfigException.class, () -> PlanBuilder.build(settings(s), new PlanBuilder.Submission(values(), films()), WHO, null, TARGET, LANGUAGES, bare));
    }

    private static Settings settings(S s) {
        try {
            return s.build();
        } catch (ConfigException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void filesWithoutAFilesFolderAreAConfigurationError() {
        PlanBuilder.Target noFiles = new PlanBuilder.Target(TARGET.parentPath(), null);
        assertThrows(ConfigException.class, () -> PlanBuilder.build(settings(new S()), new PlanBuilder.Submission(values(), films()), WHO, null, noFiles,
                LANGUAGES, CATALOG));
    }

    @Test
    void publishFollowsTheSetting() throws Exception {
        S s = new S();
        s.publish = false;
        assertFalse(build(s, values(), films(), null).publish());
    }
}
