package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.jahia.modules.formidable.engine.imports.model.ImportedContainer;
import org.jahia.modules.formidable.engine.imports.model.ImportedField;
import org.jahia.modules.formidable.engine.imports.model.ImportedForm;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

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
        assertEquals(FormsFormConverter.SOURCE_SYSTEM, form.sourceSystem());
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

        ImportedField enquiry = form.fields().toList().get(4);
        assertEquals(Map.of("en", "Your Enquiry", "fr", "Votre demande"), enquiry.titles());
        assertEquals("5", enquiry.properties().get("rows"));
        assertEquals(form.fieldBySourceId("56555cf3-abce-49e0-acdc-32e357b83c7e"), enquiry);
        assertEquals(form.fieldBySourceName("text-area_0_4"), enquiry);
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
        FormsForm form = formOf(select);

        ImportedField field = EVERYTHING.convert(form, null).fields().toList().get(0);
        assertEquals("colour", field.name());
        assertEquals(FormsFieldTypes.SELECT, field.nodeType());
        assertEquals("true", field.properties().get("multiple"));
        assertEquals(Set.of("en", "fr"), field.options().keySet());
        assertEquals("Rouge", new JSONObject(field.options().get("fr").get(0)).getString("label"));
        assertEquals("red", new JSONObject(field.options().get("fr").get(0)).getString("value"));
    }

    @Test
    void aSwitchBecomesTheExtendedTypeOrARadioWithTwoOptions() {
        FormsField definition = new FormsField("switch_0_1", "u1", "fcnt:switchDefinition", Map.of("en", "Newsletter"),
                null, Map.of(), List.of(), false, false);

        ImportedField extended = EVERYTHING.convert(formOf(definition), null).fields().toList().get(0);
        assertEquals(FormsFieldTypes.SWITCH, extended.nodeType());
        assertTrue(extended.options().isEmpty());

        ImportedField fallback = ELEMENTS_ONLY.convert(formOf(definition), null).fields().toList().get(0);
        assertEquals(FormsFieldTypes.RADIO, fallback.nodeType());
        assertEquals(2, fallback.options().get("en").size());
        assertEquals("true", new JSONObject(fallback.options().get("en").get(0)).getString("value"));
        assertTrue(fallback.report().get(0).contains("not deployed"));
    }

    @Test
    void aCountryFieldUsesTheCountrySourceWhenDeclared() {
        FormsField definition = new FormsField("country_0_1", "u1", "fcnt:countryListDefinition", Map.of("en", "Country"),
                null, Map.of(), List.of(), false, false);

        ImportedField sourced = EVERYTHING.convert(formOf(definition), null).fields().toList().get(0);
        assertEquals(FormsFieldTypes.SELECT, sourced.nodeType());
        assertEquals(FormsFormConverter.COUNTRY_SOURCE, sourced.optionsSourceKey());

        ImportedField manual = ELEMENTS_ONLY.convert(formOf(definition), null).fields().toList().get(0);
        assertNull(manual.optionsSourceKey());
        assertTrue(manual.report().stream().anyMatch(line -> line.contains("country options source")));
    }

    @Test
    void stepsAndFieldsetsBecomeContainers() {
        FormsField a = new FormsField("text_0_1", "u1", "fcnt:inputDefinition", Map.of("en", "A"), null, Map.of(), List.of(), false, false);
        FormsField start = new FormsField("fieldset_0_2", "u2", "fcnt:fieldsetStartDefinition", Map.of("en", "Address"), null, Map.of(), List.of(), false, false);
        FormsField b = new FormsField("text_0_3", "u3", "fcnt:inputDefinition", Map.of("en", "B"), null, Map.of(), List.of(), false, false);
        FormsField end = new FormsField("fieldsetEnd_0_4", "u4", "fcnt:fieldsetEndDefinition", Map.of(), null, Map.of(), List.of(), false, false);
        FormsField c = new FormsField("text_0_5", "u5", "fcnt:inputDefinition", Map.of("en", "C"), null, Map.of(), List.of(), false, false);
        FormsStep step1 = new FormsStep("step-1", 1, Map.of("en", "Step 1"), List.of(a, start, b, end));
        FormsStep step2 = new FormsStep("step-2", 2, Map.of("en", "Step 2"), List.of(c));
        FormsForm form = new FormsForm("f", "uf", "formFactory/forms/f", "en", Map.of("en", "F"), Map.of(), FormsForm.Settings.NONE, List.of(step1, step2), List.of());

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
        FormsField password = new FormsField("password_0_1", "u1", "fcnt:passwordDefinition", Map.of("en", "Password"), null, Map.of(), List.of(), false, false);
        ImportedForm form = EVERYTHING.convert(formOf(password), null);
        assertEquals(0, form.fields().count());
        assertTrue(form.report().stream().anyMatch(line -> line.contains("password") && line.contains("in clear")));
    }

    private static FormsForm formOf(FormsField... fields) {
        FormsStep step = new FormsStep("step-1", 1, Map.of(), List.of(fields));
        return new FormsForm("f", "uf", "formFactory/forms/f", "en", Map.of("en", "F"), Map.of(), FormsForm.Settings.NONE, List.of(step),
                List.of(new FormsAction("savetojcraction", FormsAction.SAVE_TO_JCR, Map.of())));
    }
}
