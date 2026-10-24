package org.jahia.modules.formidable.formsimport.jahiaforms;

import org.jahia.modules.formidable.engine.api.VisitorProfileSchema;
import org.jahia.modules.formidable.formsimport.model.ImportNote;
import org.jahia.modules.formidable.formsimport.model.ImportedContainer;
import org.jahia.modules.formidable.formsimport.model.ImportedField;
import org.jahia.modules.formidable.formsimport.model.ImportedForm;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormsFormConverterTest {

    private static final String PROPERTY = "property";
    private static final String EMAIL_PROPERTY = "email";
    private static final String CHOICES = "choices";
    private static final String INTERESTS = "interests";
    private static final FormsFormConverter EVERYTHING = new FormsFormConverter(type -> true, source -> true);
    private static final FormsFormConverter ELEMENTS_ONLY = new FormsFormConverter(type -> !type.startsWith("fmdbext:"), source -> false);

    private static FormsExport sample() throws Exception {
        return FormsExportReaderTest.sampleReader().readStructure();
    }

    @Test
    void contactUsBecomesAFormWithFiveNamedFields() throws Exception {
        FormsExport export = sample();
        FormsForm source = export.forms().get("contact-us");
        ImportedForm form = EVERYTHING.convert(source, export.resultsOf(source));

        assertEquals("contact-us", form.name());
        assertEquals(Map.of("en", "Contact Us", "fr", "Contact Us"), form.titles());
        assertEquals("en", form.buildingLang());
        assertEquals("jahia-forms", form.sourceSystem());
        assertEquals("08246a0f-de43-4dbb-b91e-55cdd366614b", form.sourceId());
        assertEquals(export.results().get("contact-us").uuid(), form.sourceResultsId());
        assertEquals("forms/contact-us", form.sourcePath());
        assertEquals(List.of("your-first-name", "your-last-name", "your-email-address", "your-telephone-number", "your-enquiry"),
                form.fields().map(ImportedField::name).toList());
        assertEquals(List.of(FormsFieldTypes.INPUT_TEXT, FormsFieldTypes.INPUT_TEXT, FormsFieldTypes.INPUT_EMAIL,
                FormsFieldTypes.INPUT_TEXT, FormsFieldTypes.TEXTAREA), form.fields().map(ImportedField::nodeType).toList());
    }

    @Test
    void aPrefillFromTheVisitorProfileBecomesTheMappingOfTheFieldWhenTheIntegrationReachesIt() throws Exception {
        FormsExport export = sample();
        FormsForm source = export.forms().get("contact-us");
        FormsFormConverter mapping = new FormsFormConverter(new ImportEnvironment(type -> true, s -> true, type -> type.equals(FormsFieldTypes.INPUT_TEXT), ImportEnvironment.ANY_PROPERTY, false));

        ImportedForm form = mapping.convert(source, export.resultsOf(source));

        ImportedField firstName = form.fields().toList().get(0);
        assertEquals(List.of(FormsFormConverter.PROFILE_MAPPING_MIXIN), firstName.mixins());
        assertEquals("firstName", firstName.properties().get(FormsFormConverter.PROFILE_PROPERTY));
        assertEquals("true", firstName.properties().get(FormsFormConverter.PROFILE_PREFILL));
        assertEquals("editable", firstName.properties().get(FormsFormConverter.PROFILE_PREFILL_THEN));
        assertTrue(has(firstName.report(), "field.profilePrefilled", PROPERTY, "firstName", "after", "editable"), firstName.report().toString());
        assertEquals("lastName", form.fields().toList().get(1).properties().get(FormsFormConverter.PROFILE_PROPERTY));
        // without the integration, or on a type its mixin does not reach, the report names the property instead
        FormsFormConverter withoutIntegration = new FormsFormConverter(type -> !type.equals(FormsFormConverter.PROFILE_MAPPING_MIXIN), s -> true);
        ImportedField unmapped = withoutIntegration.convert(source, export.resultsOf(source)).fields().toList().get(0);
        assertNull(unmapped.properties().get(FormsFormConverter.PROFILE_PROPERTY));
        assertTrue(unmapped.mixins().isEmpty());
        assertTrue(has(unmapped.report(), "field.prefillNoIntegration", PROPERTY, "firstName"), unmapped.report().toString());
        ImportedField unreachable = EVERYTHING.convert(source, export.resultsOf(source)).fields().toList().get(0);
        assertNull(unreachable.properties().get(FormsFormConverter.PROFILE_PROPERTY));
        assertTrue(has(unreachable.report(), "field.prefillNotMappable", PROPERTY, "firstName"), unreachable.report().toString());
    }

    @Test
    void aPrefillCarriesWhatFollowsTheWriteTheMappingAloneAndOneMappingPerField() {
        FormsFormConverter mapping = new FormsFormConverter(new ImportEnvironment(type -> true, s -> true, type -> true, ImportEnvironment.ANY_PROPERTY, false));
        FormsPrefill hidden = new FormsPrefill(FormsPrefill.PROFILE_PREFILL_TYPE, "firstName", true, false);
        FormsPrefill readOnly = new FormsPrefill(FormsPrefill.PROFILE_PREFILL_TYPE, "lastName", false, true);
        FormsPrefill both = new FormsPrefill(FormsPrefill.PROFILE_PREFILL_TYPE, "email", true, true);
        FormsPrefill mappingOnly = new FormsPrefill(FormsPrefill.PROFILE_MAPPING_ONLY_TYPE, "phone", false, false);
        FormsPrefill userInfo = new FormsPrefill("fcnt:userInfoPrefill", null, false, false);
        ImportedForm form = mapping.convert(formOf(
                prefilled("hidden_0_1", hidden),
                prefilled("readonly_0_2", readOnly),
                prefilled("both_0_3", both),
                prefilled("mapped_0_4", mappingOnly),
                prefilled("twice_0_5", hidden, readOnly),
                prefilled("userinfo_0_6", userInfo)), null);
        List<ImportedField> fields = form.fields().toList();

        assertEquals("hidden", fields.get(0).properties().get(FormsFormConverter.PROFILE_PREFILL_THEN));
        assertEquals("readOnly", fields.get(1).properties().get(FormsFormConverter.PROFILE_PREFILL_THEN));
        // hidden wins over read-only: a hidden field is not edited anyway
        assertEquals("hidden", fields.get(2).properties().get(FormsFormConverter.PROFILE_PREFILL_THEN));
        assertEquals("phone", fields.get(3).properties().get(FormsFormConverter.PROFILE_PROPERTY));
        assertEquals("false", fields.get(3).properties().get(FormsFormConverter.PROFILE_PREFILL));
        assertNull(fields.get(3).properties().get(FormsFormConverter.PROFILE_PREFILL_THEN));
        assertTrue(has(fields.get(3).report(), "field.profileMapped"), fields.get(3).report().toString());
        // one mapping per field: the first prefill of the kind wins, the second is reported
        assertEquals("firstName", fields.get(4).properties().get(FormsFormConverter.PROFILE_PROPERTY));
        assertTrue(has(fields.get(4).report(), "field.prefillAlreadyMapped", PROPERTY, "lastName"), fields.get(4).report().toString());
        // another kind of prefill has no counterpart
        assertNull(fields.get(5).properties().get(FormsFormConverter.PROFILE_PROPERTY));
        assertTrue(fields.get(5).mixins().isEmpty());
        assertTrue(has(fields.get(5).report(), "field.prefillOther", "prefill", "fcnt:userInfoPrefill"), fields.get(5).report().toString());
    }

    private static FormsField prefilled(String name, FormsPrefill... prefills) {
        return new FormsField(name, "uuid-" + name, "fcnt:inputDefinition", Map.of("en", name), null, Map.of(), List.of(), List.of(prefills), false);
    }

    @Test
    void aFieldKeepsItsLabelsPlaceholderSourceAndSettings() throws Exception {
        FormsExport export = sample();
        FormsForm source = export.forms().get("contact-us");
        ImportedForm form = EVERYTHING.convert(source, export.resultsOf(source));

        ImportedField firstName = form.fields().toList().get(0);
        assertEquals(Map.of("en", "Your First name", "fr", "Votre prénom"), firstName.titles());
        assertEquals(Map.of("en", "Your First name*", "fr", "Votre prénom*"), firstName.i18nProperties().get("placeholder"));
        assertNull(firstName.i18nProperties().get("helpText"));
        assertFalse(firstName.required());
        assertEquals("0249eae3-f0a8-4dc0-8b65-1d18d1f4fa9e", firstName.sourceId());
        assertEquals("text-input_0_1", firstName.sourceName());
        assertEquals("fcnt:inputDefinition", firstName.sourceType());
        assertTrue(firstName.report().stream().anyMatch(note -> note.code().startsWith("field.prefill")), firstName.report().toString());

        ImportedField email = form.fields().toList().get(2);
        assertEquals(Map.of("en", "Please enter a valid email address", "fr", "Please enter a valid email address"),
                email.i18nProperties().get(FormsFieldTypes.MSG_TYPE_MISMATCH));

        ImportedField enquiry = form.fields().toList().get(4);
        assertEquals(Map.of("en", "Your Enquiry", "fr", "Votre demande"), enquiry.titles());
        assertEquals("5", enquiry.properties().get("rows"));
        assertEquals(form.fieldBySourceId("56555cf3-abce-49e0-acdc-32e357b83c7e"), enquiry);
        assertEquals(form.fieldBySourceName("text-area_0_4"), enquiry);
    }

    @Test
    void everyPropertyOfABuiltFieldIsOneItsTypeDeclares() throws Exception {
        List<ImportedField> fields = new ArrayList<>();
        FormsExport export = sample();
        for (FormsForm source : export.forms().values()) {
            EVERYTHING.convert(source, export.resultsOf(source)).fields().forEach(fields::add);
        }
        fields.addAll(ELEMENTS_ONLY.convert(formOf(
                field("switch_0_1", "fcnt:switchDefinition", "Newsletter"),
                field("rating_0_2", "fcnt:ratingDefinition", "Rating"),
                field("terms_0_3", "fcnt:acceptTermCheckboxDefinition", "Terms"),
                field("country_0_4", "fcnt:countryListDefinition", "Country"),
                field("hidden_0_5", "fcnt:hiddenDefinition", "Hidden")), null).fields().toList());

        assertFalse(fields.isEmpty());
        for (ImportedField field : fields) {
            Set<String> accepted = FormsFieldTypes.accepted(field.nodeType());
            assertFalse(accepted.isEmpty(), field.nodeType());
            assertTrue(accepted.containsAll(field.properties().keySet()), field.name() + " " + field.properties().keySet());
            assertTrue(accepted.containsAll(field.i18nProperties().keySet()), field.name() + " " + field.i18nProperties().keySet());
            assertTrue(!field.required() || accepted.contains(ImportedField.REQUIRED_PROPERTY), field.name());
        }
    }

    @Test
    void aSettingTheTypeDoesNotHaveIsReportedNotKept() {
        FormsOption placeholder = new FormsOption("placeholder", null, Map.of("en", "Pick one"));
        FormsField select = new FormsField("select_0_1", "u1", "fcnt:selectBasicDefinition", Map.of("en", "Colour"),
                null, Map.of("placeholder", placeholder), List.of(), List.of(), false);
        FormsValidation required = new FormsValidation("required", FormsValidation.REQUIRED, Map.of());
        FormsField hidden = new FormsField("hidden_0_2", "u2", "fcnt:hiddenDefinition", Map.of("en", "Hidden"),
                null, Map.of(), List.of(required), List.of(), false);
        FormsValidation regex = new FormsValidation("regex", FormsValidation.REGEX,
                Map.of("regex", new FormsOption("regex", "^[a-z]+$", Map.of())));
        FormsField textarea = new FormsField("area_0_3", "u3", "fcnt:textAreaDefinition", Map.of("en", "Area"),
                null, Map.of(), List.of(regex), List.of(), false);

        List<ImportedField> fields = EVERYTHING.convert(formOf(select, hidden, textarea), null).fields().toList();

        // a select shows its placeholder as the label of its empty option
        assertEquals(Map.of("en", "Pick one"), fields.get(0).i18nProperties().get(FormsFieldTypes.OPTIONS_EMPTY_LABEL));
        assertNull(fields.get(0).i18nProperties().get(FormsFieldTypes.PLACEHOLDER));
        // a hidden field has no required flag
        assertFalse(fields.get(1).required());
        assertTrue(has(fields.get(1).report(), ImportedField.NOTE_SETTING, "setting", "required"), fields.get(1).report().toString());
        // a textarea has no pattern
        assertNull(fields.get(2).properties().get(FormsFieldTypes.PATTERN));
        assertTrue(has(fields.get(2).report(), "field.rule", "rule", "fcnt:regexValidation"), fields.get(2).report().toString());

        // the builder itself reports a plain setting and an i18n setting the type does not declare
        ImportedField.Builder builder = ImportedField.builder("hidden", FormsFieldTypes.INPUT_HIDDEN, FormsFieldTypes.accepted(FormsFieldTypes.INPUT_HIDDEN))
                .property(FormsFieldTypes.ROWS, "5")
                .i18nProperty(FormsFieldTypes.PLACEHOLDER, Map.of("en", "Type here"));
        ImportedField built = builder.build();
        assertTrue(built.properties().isEmpty());
        assertTrue(built.i18nProperties().isEmpty());
        assertEquals(List.of("rows", "placeholder"), built.report().stream().map(note -> note.param("setting")).toList());
        assertTrue(built.report().stream().allMatch(note -> note.code().equals(ImportedField.NOTE_SETTING) && "fmdb:inputHidden".equals(note.param("type"))));
    }

    @Test
    void theRulesCarryTheirBoundsAndMessages() {
        FormsValidation length = new FormsValidation("length", FormsValidation.RANGE_LENGTH, Map.of(
                "min", new FormsOption("min", "2", Map.of()), "max", new FormsOption("max", "40", Map.of()),
                "message", new FormsOption("message", null, Map.of("en", "Between 2 and 40"))));
        FormsValidation email = new FormsValidation("email", FormsValidation.EMAIL, Map.of());
        FormsField text = new FormsField("text_0_1", "u1", "fcnt:inputDefinition", Map.of("en", "Name"),
                null, Map.of(), List.of(length, email), List.of(), false);
        FormsValidation range = new FormsValidation("range", FormsValidation.RANGE, Map.of(
                "min", new FormsOption("min", "1", Map.of()), "max", new FormsOption("max", "10", Map.of()),
                "message", new FormsOption("message", null, Map.of("en", "From 1 to 10"))));
        FormsField number = new FormsField("number_0_2", "u2", "fcnt:numberDefinition", Map.of("en", "Score"),
                null, Map.of(), List.of(range), List.of(), false);
        FormsValidation regex = new FormsValidation("regex", FormsValidation.REGEX, Map.of(
                "regex", new FormsOption("regex", "^[A-Z]+$", Map.of()),
                "message", new FormsOption("message", null, Map.of("en", "Capitals only"))));
        FormsField code = new FormsField("code_0_3", "u3", "fcnt:inputDefinition", Map.of("en", "Code"),
                null, Map.of(), List.of(regex), List.of(), false);

        List<ImportedField> fields = EVERYTHING.convert(formOf(text, number, code), null).fields().toList();

        assertEquals("2", fields.get(0).properties().get(FormsFieldTypes.MIN_LENGTH));
        assertEquals("40", fields.get(0).properties().get(FormsFieldTypes.MAX_LENGTH));
        assertEquals(Map.of("en", "Between 2 and 40"), fields.get(0).i18nProperties().get(FormsFieldTypes.MSG_TOO_SHORT));
        assertEquals(Map.of("en", "Between 2 and 40"), fields.get(0).i18nProperties().get(FormsFieldTypes.MSG_TOO_LONG));
        // an e-mail rule on a plain text field has no equivalent
        assertTrue(has(fields.get(0).report(), "field.rule", "rule", "fcnt:emailValidation"), fields.get(0).report().toString());
        assertEquals("1", fields.get(1).properties().get(FormsFieldTypes.MIN_VALUE));
        assertEquals("10", fields.get(1).properties().get(FormsFieldTypes.MAX_VALUE));
        assertEquals(Map.of("en", "From 1 to 10"), fields.get(1).i18nProperties().get(FormsFieldTypes.MSG_RANGE_UNDERFLOW));
        assertEquals(Map.of("en", "From 1 to 10"), fields.get(1).i18nProperties().get(FormsFieldTypes.MSG_RANGE_OVERFLOW));
        assertTrue(fields.get(1).report().isEmpty(), fields.get(1).report().toString());
        assertEquals("^[A-Z]+$", fields.get(2).properties().get(FormsFieldTypes.PATTERN));
        assertEquals(Map.of("en", "Capitals only"), fields.get(2).i18nProperties().get(FormsFieldTypes.MSG_PATTERN_MISMATCH));
    }

    @Test
    void theActionsAreCarriedOrReported() throws Exception {
        FormsExport export = sample();
        FormsForm source = export.forms().get("contact-us");
        ImportedForm form = EVERYTHING.convert(source, export.resultsOf(source));

        assertEquals(1, form.actions().size());
        assertEquals("fmdb:save2jcrAction", form.actions().get(0).nodeType());
        // the redirect to a page this instance does not hold is reported (FormsRedirectsTest)
        assertTrue(has(form.report(), "form.redirectPageNotFound", "target", "/sites/motor-retail/home"), form.report().toString());
        assertFalse(has(form.report(), "form.saveForLater"), form.report().toString());
    }

    /**
     * A form that displayed a captcha gets one only on an instance that configures one. The sample forms have
     * displayCaptcha="true" but no captcha key on their formFactory, so they displayed none and get none.
     */
    @Test
    void aFormThatDisplayedACaptchaGetsOneWhenTheInstanceConfiguresIt() throws Exception {
        FormsExport export = sample();
        FormsForm sampled = export.forms().get("contact-us");
        ImportedForm none = new FormsFormConverter(type -> true, s -> true, true).convert(sampled, export.resultsOf(sampled));
        assertFalse(none.captcha());
        assertTrue(none.report().stream().noneMatch(note -> note.code().startsWith("form.captcha")), none.report().toString());

        FormsForm.Settings settings = sampled.settings();
        FormsForm source = new FormsForm(sampled.name(), sampled.uuid(), sampled.path(), sampled.buildingLang(), sampled.titles(),
                sampled.afterSubmissionText(), new FormsForm.Settings(true, settings.tracksUsers(), settings.savable(), settings.constrained()),
                sampled.steps(), sampled.actions());
        ImportedForm without = EVERYTHING.convert(source, export.resultsOf(source));
        assertFalse(without.captcha());
        assertTrue(has(without.report(), "form.captchaOff"), without.report().toString());

        ImportedForm with = new FormsFormConverter(type -> true, s -> true, true).convert(source, export.resultsOf(source));
        assertTrue(with.captcha());
        assertTrue(has(with.report(), "form.captchaOn"), with.report().toString());
    }

    /** The file rules of Forms: a JSON list of type groups with a selected flag, and a number of files (fileValidation.wzd). */
    @Test
    void aFileFieldTakesAcceptFromTheSelectedTypeGroupsAndMultipleFromTheNumberOfFiles() {
        String groups = "[{\"key\":\"all\",\"value\":\".*\",\"selected\":false},{\"key\":\"image\",\"value\":\"image/.*\",\"selected\":true},"
                + "{\"key\":\"pdf\",\"value\":\"application/pdf\",\"selected\":true},{\"key\":\"doc\",\"value\":\"application/(pdf|.*word.*)\",\"selected\":true}]";
        FormsValidation types = new FormsValidation("file", FormsValidation.FILE, Map.of(
                FormsOptionNames.FILE_TYPE, new FormsOption(FormsOptionNames.FILE_TYPE, groups, Map.of())));
        FormsValidation number = new FormsValidation("fileNumber", FormsValidation.FILE_NUMBER, Map.of(
                FormsOptionNames.FILE_NUMBER, new FormsOption(FormsOptionNames.FILE_NUMBER, "3", Map.of())));
        FormsField upload = new FormsField("file_0_1", "u1", "fcnt:fileUploadDefinition", Map.of("en", "CV"),
                null, Map.of(), List.of(types, number), List.of(), false);

        ImportedField field = EVERYTHING.convert(formOf(upload), null).fields().toList().get(0);
        assertEquals("image/*,application/pdf", field.properties().get(FormsFieldTypes.ACCEPT));
        assertEquals("true", field.properties().get(FormsFieldTypes.MULTIPLE));
        assertTrue(field.report().stream().anyMatch(note -> note.code().equals("field.fileTypes") && note.param("types").contains("doc")), field.report().toString());

        // every type allowed: nothing to restrict
        FormsValidation all = new FormsValidation("file", FormsValidation.FILE, Map.of(FormsOptionNames.FILE_TYPE,
                new FormsOption(FormsOptionNames.FILE_TYPE, "[{\"key\":\"all\",\"value\":\".*\",\"selected\":true}]", Map.of())));
        FormsField any = new FormsField("file_0_2", "u2", "fcnt:fileUploadDefinition", Map.of("en", "Any"), null, Map.of(), List.of(all), List.of(), false);
        assertNull(EVERYTHING.convert(formOf(any), null).fields().toList().get(0).properties().get(FormsFieldTypes.ACCEPT));
    }

    @Test
    void aFormWithoutBuildingLanguageConvertsWithTheNamesOfItsSource() {
        FormsField a = field("text_0_1", "fcnt:inputDefinition", "A");
        FormsStep step = new FormsStep("step-1", 1, Map.of(), List.of(a));
        FormsForm form = new FormsForm("f", "uf", "formFactory/forms/f", null, Map.of(), Map.of(), FormsForm.Settings.NONE, List.of(step), List.of());

        ImportedForm converted = EVERYTHING.convert(form, null);
        assertEquals(Map.of("", "f"), converted.titles());
        assertEquals(List.of("text_0_1"), converted.fields().map(ImportedField::name).toList());
        assertNull(converted.buildingLang());
    }

    @Test
    void aFormThatFormsDidNotSaveGetsNoSaveActionAndTheReportSaysSo() throws Exception {
        FormsExport export = sample();
        FormsForm source = export.forms().get("newsletterregistration");
        FormsForm unsaved = new FormsForm(source.name(), source.uuid(), source.path(), source.buildingLang(), source.titles(),
                source.afterSubmissionText(), source.settings(), source.steps(), List.of());

        ImportedForm form = EVERYTHING.convert(unsaved, null);
        assertTrue(form.actions().isEmpty());
        assertTrue(has(form.report(), "form.notSaved"), form.report().toString());
        assertNull(form.sourceResultsId());
        assertEquals(source.uuid(), form.sourceKey());
        assertEquals(List.of("firstname", "lastname", "enter-your-email-here"), form.fields().map(ImportedField::name).toList());
        assertTrue(form.fields().toList().get(2).required());
    }

    @Test
    void aFormDeletedInFormsIsBuiltFromItsLabelNodes() throws Exception {
        FormsResults results = sample().results().get("contact-us");

        ImportedForm form = EVERYTHING.convertFromLabels(results);
        assertNull(form.sourceId());
        assertEquals(results.uuid(), form.sourceResultsId());
        assertEquals(results.uuid(), form.sourceKey());
        assertEquals("forms/contact-us", form.sourcePath());
        // four labels are empty: those fields keep their Forms name; the fifth has a label
        assertEquals(List.of("email-input_0_2", "your-enquiry", "text-input_0_1", "text-input_0_1_copy_01", "text-input_0_5"),
                form.fields().map(ImportedField::name).toList());
        assertTrue(form.fields().allMatch(f -> FormsFieldTypes.INPUT_TEXT.equals(f.nodeType())));
        assertEquals("56555cf3-abce-49e0-acdc-32e357b83c7e", form.fieldBySourceName("text-area_0_4").sourceId());
        assertEquals("form.fromLabels", form.report().get(0).code());
    }

    @Test
    void aChoiceFieldGetsManualOptionsFromItsChoices() {
        FormsOption choices = new FormsOption("choices", null, Map.of(
                "en", "[{\"key\":\"red\",\"value\":\"Red\"}]", "fr", "[{\"key\":\"red\",\"value\":\"Rouge\"}]"));
        FormsField select = new FormsField("select_0_1", "u1", "fcnt:selectMultipleDefinition", Map.of("en", "Colour", "fr", "Couleur"),
                "choices", Map.of("choices", choices), List.of(), List.of(), false);

        ImportedField field = EVERYTHING.convert(formOf(select), null).fields().toList().get(0);
        assertEquals("colour", field.name());
        assertEquals(FormsFieldTypes.SELECT, field.nodeType());
        assertEquals("true", field.properties().get("multiple"));
        assertEquals(Set.of("en", "fr"), field.options().keySet());
        assertEquals("Rouge", new JSONObject(field.options().get("fr").get(0)).getString("label"));
        assertEquals("red", new JSONObject(field.options().get("fr").get(0)).getString("value"));
    }

    /** The switch texts are the textOn and textOff options of the definition (switchDefinition.wzd). */
    @Test
    void aSwitchBecomesTheExtendedTypeOrARadioWithTwoOptions() {
        Map<String, FormsOption> texts = Map.of(
                FormsOptionNames.TEXT_ON, new FormsOption(FormsOptionNames.TEXT_ON, null, Map.of("en", "Yes please")),
                FormsOptionNames.TEXT_OFF, new FormsOption(FormsOptionNames.TEXT_OFF, null, Map.of("en", "No thanks")));
        FormsField definition = new FormsField("switch_0_1", "u1", "fcnt:switchDefinition", Map.of("en", "Newsletter"), null, texts, List.of(), List.of(), false);

        ImportedField extended = EVERYTHING.convert(formOf(definition), null).fields().toList().get(0);
        assertEquals(FormsFieldTypes.SWITCH, extended.nodeType());
        assertTrue(extended.options().isEmpty());
        assertEquals(Map.of("en", "Yes please"), extended.i18nProperties().get(FormsFieldTypes.ON_LABEL));
        assertEquals(Map.of("en", "No thanks"), extended.i18nProperties().get(FormsFieldTypes.OFF_LABEL));

        ImportedField fallback = ELEMENTS_ONLY.convert(formOf(definition), null).fields().toList().get(0);
        assertEquals(FormsFieldTypes.RADIO, fallback.nodeType());
        assertEquals(2, fallback.options().get("en").size());
        assertEquals("true", new JSONObject(fallback.options().get("en").get(0)).getString("value"));
        assertEquals("Yes please", new JSONObject(fallback.options().get("en").get(0)).getString("label"));
        assertEquals("No thanks", new JSONObject(fallback.options().get("en").get(1)).getString("label"));
        assertNull(fallback.i18nProperties().get(FormsFieldTypes.ON_LABEL));
        assertEquals("field.createdAsFallback", fallback.report().get(0).code());
    }

    /**
     * The statement of a consent is the terms label as the visitors read it: the braces marked the text the
     * box rendered as a link to the terms file, a repository path the statement cannot hold.
     */
    @Test
    void aConsentTakesItsStatementFromTheTermsLabelAndReportsTheLinkedFile() {
        Map<String, FormsOption> terms = Map.of(
                FormsOptionNames.TERMS_LABEL, new FormsOption(FormsOptionNames.TERMS_LABEL, null,
                        Map.of("en", "I have read and agree to the {LICENSE}.", "fr", "J'ai lu {nos conditions} et je les accepte.")),
                FormsOptionNames.LINK, new FormsOption(FormsOptionNames.LINK, null, Map.of("en", "/sites/acme/files/terms.pdf")));
        FormsField withTerms = new FormsField("terms_0_1", "u1", "fcnt:acceptTermCheckboxDefinition", Map.of("en", "I agree"), null, terms, List.of(), List.of(), false);

        ImportedField consent = EVERYTHING.convert(formOf(withTerms), null).fields().toList().get(0);
        assertEquals(Map.of("en", "I have read and agree to the LICENSE.", "fr", "J'ai lu nos conditions et je les accepte."),
                consent.i18nProperties().get(FormsFieldTypes.STATEMENT));
        assertTrue(has(consent.report(), "field.termsLink", "link", "/sites/acme/files/terms.pdf"), consent.report().toString());

        // no terms file: nothing to report, the statement still reads as the visitors read it
        FormsField unlinked = new FormsField("terms_0_2", "u2", "fcnt:acceptTermCheckboxDefinition", Map.of("en", "I agree"), null,
                Map.of(FormsOptionNames.TERMS_LABEL, terms.get(FormsOptionNames.TERMS_LABEL)), List.of(), List.of(), false);
        ImportedField plain = EVERYTHING.convert(formOf(unlinked), null).fields().toList().get(0);
        assertEquals("I have read and agree to the LICENSE.", plain.i18nProperties().get(FormsFieldTypes.STATEMENT).get("en"));
        assertTrue(plain.report().isEmpty(), plain.report().toString());
    }

    @Test
    void anAcceptTermsBoxWithoutChoicesGetsOneAcceptedOptionInTheBuildingLanguage() {
        FormsField definition = new FormsField("terms_0_1", "u1", "fcnt:acceptTermCheckboxDefinition",
                Map.of("fr", "J'accepte", "en", "I agree"), null, Map.of(), List.of(), List.of(), false);

        ImportedField consent = EVERYTHING.convert(formOf(definition), null).fields().toList().get(0);
        assertEquals(FormsFieldTypes.CONSENT, consent.nodeType());
        assertEquals(Map.of("fr", "J'accepte", "en", "I agree"), consent.i18nProperties().get(FormsFieldTypes.STATEMENT));

        ImportedField checkbox = ELEMENTS_ONLY.convert(formOf(definition), null).fields().toList().get(0);
        assertEquals(FormsFieldTypes.CHECKBOX, checkbox.nodeType());
        assertEquals(Set.of("en"), checkbox.options().keySet());
        JSONObject option = new JSONObject(checkbox.options().get("en").get(0));
        assertEquals(FormsFormConverter.ACCEPTED, option.getString("value"));
        assertEquals("I agree", option.getString("label"));
    }

    @Test
    void aCountryFieldUsesTheCountrySourceWhenDeclared() {
        FormsField definition = field("country_0_1", "fcnt:countryListDefinition", "Country");

        ImportedField sourced = EVERYTHING.convert(formOf(definition), null).fields().toList().get(0);
        assertEquals(FormsFieldTypes.SELECT, sourced.nodeType());
        assertEquals(FormsFormConverter.COUNTRY_SOURCE, sourced.optionsSourceKey());

        ImportedField manual = ELEMENTS_ONLY.convert(formOf(definition), null).fields().toList().get(0);
        assertNull(manual.optionsSourceKey());
        assertTrue(has(manual.report(), "field.noCountries"), manual.report().toString());
    }

    @Test
    void stepsAndFieldsetsBecomeContainers() {
        FormsField a = field("text_0_1", "fcnt:inputDefinition", "A");
        FormsField start = field("fieldset_0_2", "fcnt:fieldsetStartDefinition", "Address");
        FormsField b = field("text_0_3", "fcnt:inputDefinition", "B");
        FormsField end = new FormsField("fieldsetEnd_0_4", "u4", "fcnt:fieldsetEndDefinition", Map.of(), null, Map.of(), List.of(), List.of(), false);
        FormsField c = field("text_0_5", "fcnt:inputDefinition", "C");
        FormsStep step1 = new FormsStep("step-1", 1, Map.of("en", "Step 1"), List.of(a, start, b, end));
        FormsStep step2 = new FormsStep("step-2", 2, Map.of("en", "Step 2"), List.of(c));
        FormsForm form = new FormsForm("f", "uf", "formFactory/forms/f", "en", Map.of("en", "F"), Map.of(), FormsForm.Settings.NONE,
                List.of(step1, step2), List.of());

        ImportedForm converted = EVERYTHING.convert(form, null);
        assertEquals(2, converted.elements().size());
        ImportedContainer first = (ImportedContainer) converted.elements().get(0);
        assertEquals(ImportedContainer.STEP, first.nodeType());
        assertEquals("step-1", first.name());
        assertEquals(2, first.children().size());
        ImportedContainer fieldset = (ImportedContainer) first.children().get(1);
        assertEquals(ImportedContainer.FIELDSET, fieldset.nodeType());
        assertEquals("address", fieldset.name());
        assertEquals(List.of("b"), fieldset.fields().map(ImportedField::name).toList());
        assertEquals(List.of("a", "b", "c"), converted.fields().map(ImportedField::name).toList());
    }

    @Test
    void aPasswordIsNotRecreatedAndTheReportSaysWhy() {
        ImportedForm form = EVERYTHING.convert(formOf(field("password_0_1", "fcnt:passwordDefinition", "Password")), null);
        assertEquals(0, form.fields().count());
        assertTrue(form.report().stream().anyMatch(note -> note.code().equals("form.passwordDropped") && note.level() == ImportNote.Level.ACTION), form.report().toString());
    }

    private static FormsField field(String name, String type, String title) {
        return new FormsField(name, "uuid-" + name, type, Map.of("en", title), null, Map.of(), List.of(), List.of(), false);
    }

    private static FormsForm formOf(FormsField... fields) {
        FormsStep step = new FormsStep("step-1", 1, Map.of(), List.of(fields));
        return new FormsForm("f", "uf", "formFactory/forms/f", "en", Map.of("en", "F"), Map.of(), FormsForm.Settings.NONE, List.of(step),
                List.of(new FormsAction("savetojcraction", FormsAction.SAVE_TO_JCR, Map.of())));
    }

    @Test
    void aPrefillIsMappedOnlyToAPropertyTheProfileServerDeclaresWithATypeTheFieldHolds() throws Exception {
        FormsExport export = sample();
        FormsForm source = export.forms().get("contact-us");
        ImportEnvironment.ProfileFit server = (fieldType, multiple, choices, property) -> switch (property) {
            case "firstName" -> VisitorProfileSchema.Fit.FITS;
            case "lastName" -> VisitorProfileSchema.Fit.INCOMPATIBLE;
            case EMAIL_PROPERTY -> VisitorProfileSchema.Fit.UNAVAILABLE;
            default -> VisitorProfileSchema.Fit.UNKNOWN;
        };
        List<ImportedField> fields = new FormsFormConverter(new ImportEnvironment(type -> true, s -> true, type -> true, server, false))
                .convert(source, export.resultsOf(source)).fields().toList();

        assertEquals("firstName", fields.get(0).properties().get(FormsFormConverter.PROFILE_PROPERTY));
        // an incompatible or unknown property would be skipped by the mapping rule: not written, to redo by hand
        assertNull(fields.get(1).properties().get(FormsFormConverter.PROFILE_PROPERTY));
        assertTrue(fields.get(1).mixins().isEmpty());
        assertTrue(has(fields.get(1).report(), "field.prefillIncompatible", PROPERTY, "lastName"), fields.get(1).report().toString());
        // a profile server that cannot be asked leaves the mapping as the source had it, with a line to check it
        assertEquals(EMAIL_PROPERTY, fields.get(2).properties().get(FormsFormConverter.PROFILE_PROPERTY));
        assertTrue(has(fields.get(2).report(), "field.profileUnchecked", PROPERTY, EMAIL_PROPERTY), fields.get(2).report().toString());
        assertNull(fields.get(4).properties().get(FormsFormConverter.PROFILE_PROPERTY));
        assertTrue(has(fields.get(4).report(), "field.prefillUnknownProperty", PROPERTY, "sfdc__leadComments"), fields.get(4).report().toString());
    }

    @Test
    void theProfileCheckAsksWithTheCardinalityTheMappingRuleReadsOffTheField() {
        List<String> asked = new ArrayList<>();
        ImportEnvironment.ProfileFit server = (fieldType, multiple, choices, property) -> {
            asked.add(fieldType + "|" + multiple + "|" + (choices.isPresent() ? String.valueOf(choices.getAsInt()) : "-"));
            // a multivalued property: it fits a field that holds several values only
            return multiple || (choices.isPresent() && choices.getAsInt() != 1) ? VisitorProfileSchema.Fit.FITS : VisitorProfileSchema.Fit.INCOMPATIBLE;
        };
        FormsFormConverter converter = new FormsFormConverter(new ImportEnvironment(type -> true, s -> true, type -> true, server, false));
        FormsPrefill interests = new FormsPrefill(FormsPrefill.PROFILE_PREFILL_TYPE, INTERESTS, false, false);
        FormsOption two = new FormsOption(CHOICES, null, Map.of("en", "[{\"key\":\"red\"},{\"key\":\"blue\"}]"));
        FormsOption one = new FormsOption(CHOICES, null, Map.of("en", "[{\"key\":\"yes\"}]"));
        FormsField multipleSelect = new FormsField("multi_0_1", "u1", "fcnt:selectMultipleDefinition", Map.of("en", "Colours"),
                CHOICES, Map.of(CHOICES, two), List.of(), List.of(interests), false);
        FormsField singleBox = new FormsField("box_0_2", "u2", "fcnt:multipleCheckBoxesDefinition", Map.of("en", "Agree"),
                CHOICES, Map.of(CHOICES, one), List.of(), List.of(interests), false);
        FormsField group = new FormsField("group_0_3", "u3", "fcnt:multipleCheckBoxesDefinition", Map.of("en", "Topics"),
                CHOICES, Map.of(CHOICES, two), List.of(), List.of(interests), false);

        List<ImportedField> fields = converter.convert(formOf(multipleSelect, singleBox, group), null).fields().toList();

        // the select made from a multiple select holds several values, a checkbox with one option one value, a group several
        assertEquals(List.of(FormsFieldTypes.SELECT + "|true|-", FormsFieldTypes.CHECKBOX + "|false|1", FormsFieldTypes.CHECKBOX + "|false|2"), asked);
        assertEquals(INTERESTS, fields.get(0).properties().get(FormsFormConverter.PROFILE_PROPERTY));
        assertTrue(has(fields.get(1).report(), "field.prefillIncompatible", PROPERTY, INTERESTS), fields.get(1).report().toString());
        assertEquals(INTERESTS, fields.get(2).properties().get(FormsFormConverter.PROFILE_PROPERTY));
    }

    /** Whether the notes hold one of that code whose parameters include the given name, value pairs. */
    static boolean has(List<ImportNote> notes, String code, String... params) {
        return notes.stream().anyMatch(note -> {
            if (!note.code().equals(code)) {
                return false;
            }
            for (int i = 0; i < params.length; i += 2) {
                if (!params[i + 1].equals(note.param(params[i]))) {
                    return false;
                }
            }
            return true;
        });
    }
}
