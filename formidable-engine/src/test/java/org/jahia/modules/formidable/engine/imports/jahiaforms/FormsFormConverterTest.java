package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.jahia.modules.formidable.engine.imports.model.ImportedContainer;
import org.jahia.modules.formidable.engine.imports.model.ImportedField;
import org.jahia.modules.formidable.engine.imports.model.ImportedForm;
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
        assertTrue(firstName.report().stream().anyMatch(line -> line.contains("prefill")));

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
                null, Map.of("placeholder", placeholder), List.of(), false, false);
        FormsValidation required = new FormsValidation("required", FormsValidation.REQUIRED, Map.of());
        FormsField hidden = new FormsField("hidden_0_2", "u2", "fcnt:hiddenDefinition", Map.of("en", "Hidden"),
                null, Map.of(), List.of(required), false, false);
        FormsValidation regex = new FormsValidation("regex", FormsValidation.REGEX,
                Map.of("regex", new FormsOption("regex", "^[a-z]+$", Map.of())));
        FormsField textarea = new FormsField("area_0_3", "u3", "fcnt:textAreaDefinition", Map.of("en", "Area"),
                null, Map.of(), List.of(regex), false, false);

        List<ImportedField> fields = EVERYTHING.convert(formOf(select, hidden, textarea), null).fields().toList();

        // a select shows its placeholder as the label of its empty option
        assertEquals(Map.of("en", "Pick one"), fields.get(0).i18nProperties().get(FormsFieldTypes.OPTIONS_EMPTY_LABEL));
        assertNull(fields.get(0).i18nProperties().get(FormsFieldTypes.PLACEHOLDER));
        // a hidden field has no required flag
        assertFalse(fields.get(1).required());
        assertTrue(fields.get(1).report().stream().anyMatch(line -> line.contains("required not carried over")), fields.get(1).report().toString());
        // a textarea has no pattern
        assertNull(fields.get(2).properties().get(FormsFieldTypes.PATTERN));
        assertTrue(fields.get(2).report().stream().anyMatch(line -> line.contains("fcnt:regexValidation not carried over")), fields.get(2).report().toString());

        // the builder itself reports a plain setting and an i18n setting the type does not declare
        ImportedField.Builder builder = ImportedField.builder("hidden", FormsFieldTypes.INPUT_HIDDEN, FormsFieldTypes.accepted(FormsFieldTypes.INPUT_HIDDEN))
                .property(FormsFieldTypes.ROWS, "5")
                .i18nProperty(FormsFieldTypes.PLACEHOLDER, Map.of("en", "Type here"));
        ImportedField built = builder.build();
        assertTrue(built.properties().isEmpty());
        assertTrue(built.i18nProperties().isEmpty());
        assertEquals(List.of(
                "rows (5) not carried over: fmdb:inputHidden has no such setting",
                "placeholder not carried over: fmdb:inputHidden has no such setting"), built.report());
    }

    @Test
    void theRulesCarryTheirBoundsAndMessages() {
        FormsValidation length = new FormsValidation("length", FormsValidation.RANGE_LENGTH, Map.of(
                "min", new FormsOption("min", "2", Map.of()), "max", new FormsOption("max", "40", Map.of()),
                "message", new FormsOption("message", null, Map.of("en", "Between 2 and 40"))));
        FormsValidation email = new FormsValidation("email", FormsValidation.EMAIL, Map.of());
        FormsField text = new FormsField("text_0_1", "u1", "fcnt:inputDefinition", Map.of("en", "Name"),
                null, Map.of(), List.of(length, email), false, false);
        FormsValidation range = new FormsValidation("range", FormsValidation.RANGE, Map.of(
                "min", new FormsOption("min", "1", Map.of()), "max", new FormsOption("max", "10", Map.of()),
                "message", new FormsOption("message", null, Map.of("en", "From 1 to 10"))));
        FormsField number = new FormsField("number_0_2", "u2", "fcnt:numberDefinition", Map.of("en", "Score"),
                null, Map.of(), List.of(range), false, false);
        FormsValidation regex = new FormsValidation("regex", FormsValidation.REGEX, Map.of(
                "regex", new FormsOption("regex", "^[A-Z]+$", Map.of()),
                "message", new FormsOption("message", null, Map.of("en", "Capitals only"))));
        FormsField code = new FormsField("code_0_3", "u3", "fcnt:inputDefinition", Map.of("en", "Code"),
                null, Map.of(), List.of(regex), false, false);

        List<ImportedField> fields = EVERYTHING.convert(formOf(text, number, code), null).fields().toList();

        assertEquals("2", fields.get(0).properties().get(FormsFieldTypes.MIN_LENGTH));
        assertEquals("40", fields.get(0).properties().get(FormsFieldTypes.MAX_LENGTH));
        assertEquals(Map.of("en", "Between 2 and 40"), fields.get(0).i18nProperties().get(FormsFieldTypes.MSG_TOO_SHORT));
        assertEquals(Map.of("en", "Between 2 and 40"), fields.get(0).i18nProperties().get(FormsFieldTypes.MSG_TOO_LONG));
        // an e-mail rule on a plain text field has no equivalent
        assertTrue(fields.get(0).report().stream().anyMatch(line -> line.contains("fcnt:emailValidation not carried over")), fields.get(0).report().toString());
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
        assertTrue(form.report().stream().anyMatch(line -> line.contains("fcnt:redirectToAPageAction") && line.contains("/sites/motor-retail/home")),
                form.report().toString());
        // the sample forms display a captcha (displayCaptcha="true") and track their users; neither is a form setting here
        assertTrue(form.captcha());
        assertTrue(form.report().stream().anyMatch(line -> line.contains("captcha")));
        assertFalse(form.report().stream().anyMatch(line -> line.contains("save the form for later")), form.report().toString());
    }

    @Test
    void aFormThatFormsDidNotSaveGetsNoSaveActionAndTheReportSaysSo() throws Exception {
        FormsExport export = sample();
        FormsForm source = export.forms().get("newsletterregistration");
        FormsForm unsaved = new FormsForm(source.name(), source.uuid(), source.path(), source.buildingLang(), source.titles(),
                source.afterSubmissionText(), source.settings(), source.steps(), List.of());

        ImportedForm form = EVERYTHING.convert(unsaved, null);
        assertTrue(form.actions().isEmpty());
        assertTrue(form.report().stream().anyMatch(line -> line.contains("add Save to JCR")));
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
        assertTrue(form.report().get(0).contains("no longer holds the form"));
    }

    @Test
    void aChoiceFieldGetsManualOptionsFromItsChoices() {
        FormsOption choices = new FormsOption("choices", null, Map.of(
                "en", "[{\"key\":\"red\",\"value\":\"Red\"}]", "fr", "[{\"key\":\"red\",\"value\":\"Rouge\"}]"));
        FormsField select = new FormsField("select_0_1", "u1", "fcnt:selectMultipleDefinition", Map.of("en", "Colour", "fr", "Couleur"),
                "choices", Map.of("choices", choices), List.of(), false, false);

        ImportedField field = EVERYTHING.convert(formOf(select), null).fields().toList().get(0);
        assertEquals("colour", field.name());
        assertEquals(FormsFieldTypes.SELECT, field.nodeType());
        assertEquals("true", field.properties().get("multiple"));
        assertEquals(Set.of("en", "fr"), field.options().keySet());
        assertEquals("Rouge", new JSONObject(field.options().get("fr").get(0)).getString("label"));
        assertEquals("red", new JSONObject(field.options().get("fr").get(0)).getString("value"));
    }

    @Test
    void aSwitchBecomesTheExtendedTypeOrARadioWithTwoOptions() {
        FormsField definition = field("switch_0_1", "fcnt:switchDefinition", "Newsletter");

        ImportedField extended = EVERYTHING.convert(formOf(definition), null).fields().toList().get(0);
        assertEquals(FormsFieldTypes.SWITCH, extended.nodeType());
        assertTrue(extended.options().isEmpty());

        ImportedField fallback = ELEMENTS_ONLY.convert(formOf(definition), null).fields().toList().get(0);
        assertEquals(FormsFieldTypes.RADIO, fallback.nodeType());
        assertEquals(2, fallback.options().get("en").size());
        assertEquals("true", new JSONObject(fallback.options().get("en").get(0)).getString("value"));
        assertNull(fallback.i18nProperties().get(FormsFieldTypes.ON_LABEL));
        assertTrue(fallback.report().get(0).contains("not deployed"));
    }

    @Test
    void anAcceptTermsBoxWithoutChoicesGetsOneAcceptedOptionInTheBuildingLanguage() {
        FormsField definition = new FormsField("terms_0_1", "u1", "fcnt:acceptTermCheckboxDefinition",
                Map.of("fr", "J'accepte", "en", "I agree"), null, Map.of(), List.of(), false, false);

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
        assertTrue(manual.report().stream().anyMatch(line -> line.contains("country options source")));
    }

    @Test
    void stepsAndFieldsetsBecomeContainers() {
        FormsField a = field("text_0_1", "fcnt:inputDefinition", "A");
        FormsField start = field("fieldset_0_2", "fcnt:fieldsetStartDefinition", "Address");
        FormsField b = field("text_0_3", "fcnt:inputDefinition", "B");
        FormsField end = new FormsField("fieldsetEnd_0_4", "u4", "fcnt:fieldsetEndDefinition", Map.of(), null, Map.of(), List.of(), false, false);
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
        assertTrue(form.report().stream().anyMatch(line -> line.contains("password") && line.contains("in clear")));
    }

    private static FormsField field(String name, String type, String title) {
        return new FormsField(name, "uuid-" + name, type, Map.of("en", title), null, Map.of(), List.of(), false, false);
    }

    private static FormsForm formOf(FormsField... fields) {
        FormsStep step = new FormsStep("step-1", 1, Map.of(), List.of(fields));
        return new FormsForm("f", "uf", "formFactory/forms/f", "en", Map.of("en", "F"), Map.of(), FormsForm.Settings.NONE, List.of(step),
                List.of(new FormsAction("savetojcraction", FormsAction.SAVE_TO_JCR, Map.of())));
    }
}
