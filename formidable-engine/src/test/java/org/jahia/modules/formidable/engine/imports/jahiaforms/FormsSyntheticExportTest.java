package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.jahia.modules.formidable.engine.imports.model.ImportedField;
import org.jahia.modules.formidable.engine.imports.model.ImportedForm;
import org.jahia.modules.formidable.engine.imports.model.ImportedSubmission;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hand-written export for the cases the sample lacks: a field renamed in Forms whose old answers are
 * tied to it by the label node's {@code fieldId} alone, a multi-valued answer with an encoded space inside
 * one value, a password answer, and an e-mail action with copies.
 */
class FormsSyntheticExportTest {

    private static final String EMAIL_ID = "e0000000-0000-0000-0000-000000000001";
    private static final String COLOURS_ID = "c0000000-0000-0000-0000-000000000002";
    private static final String PASSWORD_ID = "d0000000-0000-0000-0000-000000000003";

    private static final String EXPORT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<formFactory xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:j=\"http://www.jahia.org/jahia/1.0\" jcr:primaryType=\"fcnt:formFactory\">\n"
            + "  <results jcr:primaryType=\"fcnt:resultsFolder\">\n"
            + "    <survey jcr:primaryType=\"fcnt:formResults\" jcr:uuid=\"r0000000-0000-0000-0000-000000000009\" parentForm=\"#/forms/survey\" buildingLang=\"en\">\n"
            + "      <labels jcr:primaryType=\"fcnt:resultLabels\">\n"
            + "        <email jcr:primaryType=\"fcnt:definitionOptionsTranslatable\" fieldId=\"" + EMAIL_ID + "\"><j:translation_en jcr:primaryType=\"jnt:translation\" label=\"\"/></email>\n"
            + "        <colours jcr:primaryType=\"fcnt:definitionOptionsTranslatable\" fieldId=\"" + COLOURS_ID + "\"><j:translation_en jcr:primaryType=\"jnt:translation\" label=\"Colours\"/></colours>\n"
            + "        <password_0_1 jcr:primaryType=\"fcnt:definitionOptionsTranslatable\" fieldId=\"" + PASSWORD_ID + "\"/>\n"
            + "      </labels>\n"
            + "      <submissions jcr:primaryType=\"fcnt:submissions\">\n"
            + "        <_x0030_6 jcr:primaryType=\"fcnt:splittedResult\">\n"
            + "          <s1 jcr:primaryType=\"fcnt:result\" jcr:created=\"2024-06-01T10:00:00.000Z\" jcr:createdBy=\"guest\" origin=\"https://example.com/survey\">\n"
            + "            <email-input_0_2 jcr:primaryType=\"fcnt:resultField\" label=\"#/results/survey/labels/email\" result=\"user1@example.com\" optional=\"false\"/>\n"
            + "            <colours jcr:primaryType=\"fcnt:resultField\" label=\"#/results/survey/labels/colours\" result=\"Very_x0020_good Fair\" optional=\"true\"/>\n"
            + "            <password_0_1 jcr:primaryType=\"fcnt:resultField\" label=\"#/results/survey/labels/password_0_1\" result=\"**********\" optional=\"true\"/>\n"
            + "          </s1>\n"
            + "        </_x0030_6>\n"
            + "      </submissions>\n"
            + "    </survey>\n"
            + "  </results>\n"
            + "  <forms jcr:primaryType=\"fcnt:formsFolder\">\n"
            + "    <survey jcr:primaryType=\"fcnt:form\" jcr:uuid=\"f0000000-0000-0000-0000-000000000008\" buildingLang=\"en\">\n"
            + "      <j:translation_en jcr:primaryType=\"jnt:translation\" jcr:title=\"Survey\"/>\n"
            + "      <step-1 jcr:primaryType=\"fcnt:step\" stepNumber=\"1\">\n"
            + "        <email-address jcr:primaryType=\"fcnt:emailDefinition\" jcr:uuid=\"" + EMAIL_ID + "\"><j:translation_en jcr:primaryType=\"jnt:translation\" jcr:title=\"Your e-mail\"/>\n"
            + "          <validations jcr:primaryType=\"fcnt:validationRules\"><required jcr:primaryType=\"fcnt:requiredValidation\"><message jcr:primaryType=\"fcnt:definitionOptionsTranslatable\"><j:translation_en jcr:primaryType=\"jnt:translation\" jsonValue=\"We need it\"/></message></required></validations>\n"
            + "        </email-address>\n"
            + "        <colours jcr:primaryType=\"fcnt:multipleCheckBoxesDefinition\" jcr:uuid=\"" + COLOURS_ID + "\" choiceField=\"choices\"><j:translation_en jcr:primaryType=\"jnt:translation\" jcr:title=\"Colours\"/>\n"
            + "          <choices jcr:primaryType=\"fcnt:definitionOptionsTranslatable\"><j:translation_en jcr:primaryType=\"jnt:translation\" jsonValue=\"[{&quot;key&quot;:&quot;Very good&quot;,&quot;value&quot;:&quot;Very good&quot;},{&quot;key&quot;:&quot;Fair&quot;,&quot;value&quot;:&quot;Fair&quot;}]\"/></choices>\n"
            + "        </colours>\n"
            + "        <password_0_1 jcr:primaryType=\"fcnt:passwordDefinition\" jcr:uuid=\"" + PASSWORD_ID + "\"><j:translation_en jcr:primaryType=\"jnt:translation\" jcr:title=\"Password\"/></password_0_1>\n"
            + "      </step-1>\n"
            + "      <actions jcr:primaryType=\"fcnt:action\">\n"
            + "        <savetojcraction jcr:primaryType=\"fcnt:saveToJcrAction\"/>\n"
            + "        <sendemailaction jcr:primaryType=\"fcnt:sendEmailAction\">\n"
            + "          <to jcr:primaryType=\"fcnt:definitionOptions\" jsonValue=\"[&quot;team@example.com&quot;,&quot;sales@example.com&quot;]\"/>\n"
            + "          <cc jcr:primaryType=\"fcnt:definitionOptions\" jsonValue=\"boss@example.com\"/>\n"
            + "          <bcc jcr:primaryType=\"fcnt:definitionOptions\" jsonValue=\"audit@example.com\"/>\n"
            + "          <from jcr:primaryType=\"fcnt:definitionOptions\" jsonValue=\"noreply@example.com\"/>\n"
            + "          <subject jcr:primaryType=\"fcnt:definitionOptionsTranslatable\"><j:translation_en jcr:primaryType=\"jnt:translation\" jsonValue=\"New answer\"/></subject>\n"
            + "        </sendemailaction>\n"
            + "      </actions>\n"
            + "    </survey>\n"
            + "  </forms>\n"
            + "</formFactory>\n";

    private static FormsExportReader reader() throws Exception {
        return new FormsExportReader(new FormsExportZip(FormsExportReaderTest.zipOf(Map.of(FormsExportZip.XML, EXPORT))));
    }

    @Test
    void anAnswerUnderTheOldNameOfARenamedFieldIsTiedToTheFieldByTheLabelFieldId() throws Exception {
        FormsExport export = reader().readStructure();
        FormsForm source = export.forms().get("survey");
        FormsResults results = export.results().get("survey");
        ImportedForm form = new FormsFormConverter(t -> true, s -> true).convert(source, results);
        List<FormsSubmission> submissions = new ArrayList<>();
        reader().readSubmissions(submissions::add);

        ImportedSubmission converted = new FormsSubmissionConverter(form, source, results).convert(submissions.get(0));

        // the field is named "email-address" in Forms, its label node "email", the answer "email-input_0_2"
        assertEquals("jahia-forms", form.sourceSystem());
        assertEquals(List.of("user1@example.com"), converted.values().get("your-e-mail"));
        assertFalse(converted.values().containsKey("email-input_0_2"));
        assertFalse(converted.values().containsKey("email"));
        assertEquals("f0000000-0000-0000-0000-000000000008", converted.sourceFormId());
    }

    @Test
    void aMultiValuedAnswerKeepsTheSpaceInsideAValue() throws Exception {
        FormsExport export = reader().readStructure();
        FormsForm source = export.forms().get("survey");
        FormsResults results = export.results().get("survey");
        ImportedForm form = new FormsFormConverter(t -> true, s -> true).convert(source, results);
        List<FormsSubmission> submissions = new ArrayList<>();
        reader().readSubmissions(submissions::add);

        ImportedSubmission converted = new FormsSubmissionConverter(form, source, results).convert(submissions.get(0));
        assertEquals(List.of("Very good", "Fair"), converted.values().get("colours"));
    }

    @Test
    void aPasswordAnswerIsDroppedEvenThoughNoFieldWasCreated() throws Exception {
        FormsExport export = reader().readStructure();
        FormsForm source = export.forms().get("survey");
        FormsResults results = export.results().get("survey");
        ImportedForm form = new FormsFormConverter(t -> true, s -> true).convert(source, results);
        assertEquals(List.of("your-e-mail", "colours"), form.fields().map(ImportedField::name).toList());
        List<FormsSubmission> submissions = new ArrayList<>();
        reader().readSubmissions(submissions::add);

        ImportedSubmission converted = new FormsSubmissionConverter(form, source, results).convert(submissions.get(0));
        assertFalse(converted.values().containsKey("password_0_1"));
        assertTrue(converted.values().values().stream().flatMap(List::stream).noneMatch(FormsValues.PASSWORD_PLACEHOLDER::equals));
        assertEquals(List.of("answer password_0_1: password dropped"), converted.report());
    }

    @Test
    void aRequiredRuleCarriesItsMessageAndAnEmailActionKeepsOnlyItsToRecipients() throws Exception {
        FormsExport export = reader().readStructure();
        FormsForm source = export.forms().get("survey");
        ImportedForm form = new FormsFormConverter(t -> true, s -> true).convert(source, export.results().get("survey"));

        ImportedField email = form.fieldBySourceName("email-address");
        assertTrue(email.required());
        assertEquals(Map.of("en", "We need it"), email.i18nProperties().get(FormsFieldTypes.MSG_VALUE_MISSING));

        assertEquals(2, form.actions().size());
        assertEquals("fmdb:emailNotificationAction", form.actions().get(1).nodeType());
        // a recipients list Forms stored as a JSON string table reads as Forms reads it
        assertEquals("team@example.com,sales@example.com", form.actions().get(1).properties().get("to"));
        assertEquals("noreply@example.com", form.actions().get(1).properties().get("from"));
        assertEquals(Map.of("en", "New answer"), form.actions().get(1).i18nProperties().get("subject"));
        assertTrue(form.report().stream().anyMatch(line -> line.contains("CC/BCC") && line.contains("boss@example.com") && line.contains("audit@example.com")),
                form.report().toString());
    }
}
