package org.jahia.modules.formidable.formsimport.jahiaforms;

import org.jahia.modules.formidable.formsimport.model.ImportedContainer;
import org.jahia.modules.formidable.formsimport.model.ImportedElement;
import org.jahia.modules.formidable.formsimport.model.ImportedField;
import org.jahia.modules.formidable.formsimport.model.ImportedForm;
import org.jahia.modules.formidable.formsimport.model.ImportNote;
import org.jahia.modules.formidable.formsimport.model.ImportedSubmission;
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
    private static final String SATISFACTION_ID = "a0000000-0000-0000-0000-000000000004";
    private static final String EXTRAS_ID = "b0000000-0000-0000-0000-000000000005";
    // the rows and columns of a matrix as the design view stores them, a hidden row among them
    private static final String SATISFACTION_ROWS = "[{&quot;key&quot;:&quot;q1&quot;,&quot;value&quot;:&quot;The price&quot;,&quot;visible&quot;:true},"
            + "{&quot;key&quot;:&quot;q2&quot;,&quot;value&quot;:&quot;The service&quot;,&quot;visible&quot;:true},"
            + "{&quot;key&quot;:&quot;q3&quot;,&quot;value&quot;:&quot;Hidden row&quot;,&quot;visible&quot;:false}]";
    private static final String SATISFACTION_COLUMNS = "[{&quot;key&quot;:&quot;poor&quot;,&quot;value&quot;:&quot;Poor&quot;,&quot;visible&quot;:true},"
            + "{&quot;key&quot;:&quot;good&quot;,&quot;value&quot;:&quot;Good&quot;,&quot;visible&quot;:true}]";
    private static final String EXTRAS_ROWS = "[{&quot;key&quot;:&quot;r1&quot;,&quot;value&quot;:&quot;Delivery&quot;,&quot;visible&quot;:true}]";
    private static final String EXTRAS_COLUMNS = "[{&quot;key&quot;:&quot;fast&quot;,&quot;value&quot;:&quot;Fast&quot;},{&quot;key&quot;:&quot;cheap&quot;,&quot;value&quot;:&quot;Cheap&quot;}]";

    private static final String EXPORT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<formFactory xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:j=\"http://www.jahia.org/jahia/1.0\" jcr:primaryType=\"fcnt:formFactory\">\n"
            + "  <results jcr:primaryType=\"fcnt:resultsFolder\">\n"
            + "    <survey jcr:primaryType=\"fcnt:formResults\" jcr:uuid=\"r0000000-0000-0000-0000-000000000009\" parentForm=\"#/forms/survey\" buildingLang=\"en\">\n"
            + "      <labels jcr:primaryType=\"fcnt:resultLabels\">\n"
            + "        <email jcr:primaryType=\"fcnt:definitionOptionsTranslatable\" fieldId=\"" + EMAIL_ID + "\"><j:translation_en jcr:primaryType=\"jnt:translation\" label=\"\"/></email>\n"
            + "        <colours jcr:primaryType=\"fcnt:definitionOptionsTranslatable\" fieldId=\"" + COLOURS_ID + "\"><j:translation_en jcr:primaryType=\"jnt:translation\" label=\"Colours\"/></colours>\n"
            + "        <password_0_1 jcr:primaryType=\"fcnt:definitionOptionsTranslatable\" fieldId=\"" + PASSWORD_ID + "\"/>\n"
            + "        <satisfaction jcr:primaryType=\"fcnt:definitionOptionsTranslatable\" fieldId=\"" + SATISFACTION_ID + "\"><j:translation_en jcr:primaryType=\"jnt:translation\" label=\"Satisfaction\"/></satisfaction>\n"
            + "        <extras jcr:primaryType=\"fcnt:definitionOptionsTranslatable\" fieldId=\"" + EXTRAS_ID + "\"><j:translation_en jcr:primaryType=\"jnt:translation\" label=\"Extras\"/></extras>\n"
            + "      </labels>\n"
            + "      <submissions jcr:primaryType=\"fcnt:submissions\">\n"
            + "        <_x0030_6 jcr:primaryType=\"fcnt:splittedResult\">\n"
            + "          <s1 jcr:primaryType=\"fcnt:result\" jcr:created=\"2024-06-01T10:00:00.000Z\" jcr:createdBy=\"guest\" origin=\"https://example.com/survey\">\n"
            + "            <email-input_0_2 jcr:primaryType=\"fcnt:resultField\" label=\"#/results/survey/labels/email\" result=\"user1@example.com\" optional=\"false\"/>\n"
            + "            <colours jcr:primaryType=\"fcnt:resultField\" label=\"#/results/survey/labels/colours\" result=\"Very_x0020_good Fair\" optional=\"true\"/>\n"
            + "            <password_0_1 jcr:primaryType=\"fcnt:resultField\" label=\"#/results/survey/labels/password_0_1\" result=\"**********\" optional=\"true\"/>\n"
            + "            <satisfaction jcr:primaryType=\"fcnt:resultField\" label=\"#/results/survey/labels/satisfaction\" result=\"{&quot;q1&quot;:&quot;good&quot;,&quot;q2&quot;:&quot;poor&quot;,&quot;rendererName&quot;:&quot;matrixRadios&quot;}\" optional=\"true\"/>\n"
            + "            <extras jcr:primaryType=\"fcnt:resultField\" label=\"#/results/survey/labels/extras\" result=\"{&quot;rendererName&quot;:&quot;matrixCheckboxes&quot;,&quot;r1&quot;:[&quot;fast&quot;,&quot;cheap&quot;]}\" optional=\"true\"/>\n"
            + "          </s1>\n"
            // answered while the third row was still visible: that row has no field of its own now
            + "          <s2 jcr:primaryType=\"fcnt:result\" jcr:created=\"2024-06-02T10:00:00.000Z\" jcr:createdBy=\"guest\" origin=\"https://example.com/survey\">\n"
            + "            <satisfaction jcr:primaryType=\"fcnt:resultField\" label=\"#/results/survey/labels/satisfaction\" result=\"{&quot;q1&quot;:&quot;good&quot;,&quot;q2&quot;:&quot;good&quot;,&quot;q3&quot;:&quot;poor&quot;,&quot;rendererName&quot;:&quot;matrixRadios&quot;}\" optional=\"true\"/>\n"
            + "          </s2>\n"
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
            + "          <choices jcr:primaryType=\"fcnt:definitionOptionsTranslatable\"><j:translation_en jcr:primaryType=\"jnt:translation\" jsonValue=\"[{&quot;key&quot;:&quot;Very good&quot;,&quot;value&quot;:&quot;Very good&quot;},{&quot;key&quot;:&quot;Fair&quot;,&quot;value&quot;:&quot;Fair&quot;},{&quot;key&quot;:&quot;Grey&quot;,&quot;value&quot;:&quot;Grey&quot;,&quot;visible&quot;:false}]\"/></choices>\n"
            + "        </colours>\n"
            // a Forms fieldset around a matrix: a field before it, the matrix, a field after it
            + "        <about-you jcr:primaryType=\"fcnt:fieldsetStartDefinition\"><j:translation_en jcr:primaryType=\"jnt:translation\" jcr:title=\"About you\"/></about-you>\n"
            + "        <nickname jcr:primaryType=\"fcnt:inputDefinition\" jcr:uuid=\"n0000000-0000-0000-0000-000000000006\"><j:translation_en jcr:primaryType=\"jnt:translation\" jcr:title=\"Nickname\"/></nickname>\n"
            + "        <extras jcr:primaryType=\"fcnt:matrixCheckBoxesDefinition\" jcr:uuid=\"" + EXTRAS_ID + "\" choiceField=\"rows,columns\"><j:translation_en jcr:primaryType=\"jnt:translation\" jcr:title=\"Extras\"/>\n"
            + "          <helptext jcr:primaryType=\"fcnt:definitionOptionsTranslatable\"><j:translation_en jcr:primaryType=\"jnt:translation\" jsonValue=\"Tick all that apply\"/></helptext>\n"
            + "          <rows jcr:primaryType=\"fcnt:definitionOptionsTranslatable\"><j:translation_en jcr:primaryType=\"jnt:translation\" jsonValue=\"" + EXTRAS_ROWS + "\"/></rows>\n"
            + "          <columns jcr:primaryType=\"fcnt:definitionOptionsTranslatable\"><j:translation_en jcr:primaryType=\"jnt:translation\" jsonValue=\"" + EXTRAS_COLUMNS + "\"/></columns>\n"
            + "          <validations jcr:primaryType=\"fcnt:validationRules\"><one jcr:primaryType=\"fcnt:matrixRequiredValidation\"/></validations>\n"
            + "        </extras>\n"
            + "        <city jcr:primaryType=\"fcnt:inputDefinition\" jcr:uuid=\"c0000000-0000-0000-0000-000000000007\"><j:translation_en jcr:primaryType=\"jnt:translation\" jcr:title=\"City\"/></city>\n"
            // a fieldset start while one is open: Forms ends the open one there, it never nests them
            + "        <more jcr:primaryType=\"fcnt:fieldsetStartDefinition\"><j:translation_en jcr:primaryType=\"jnt:translation\" jcr:title=\"More\"/></more>\n"
            + "        <age jcr:primaryType=\"fcnt:inputDefinition\" jcr:uuid=\"a0000000-0000-0000-0000-000000000008\"><j:translation_en jcr:primaryType=\"jnt:translation\" jcr:title=\"Age\"/></age>\n"
            + "        <about-you-end jcr:primaryType=\"fcnt:fieldsetEndDefinition\"/>\n"
            // a Forms fieldset holding a matrix alone
            + "        <your-opinion jcr:primaryType=\"fcnt:fieldsetStartDefinition\"><j:translation_en jcr:primaryType=\"jnt:translation\" jcr:title=\"Your opinion\"/></your-opinion>\n"
            + "        <satisfaction jcr:primaryType=\"fcnt:matrixRadiosDefinition\" jcr:uuid=\"" + SATISFACTION_ID + "\" choiceField=\"rows,columns\"><j:translation_en jcr:primaryType=\"jnt:translation\" jcr:title=\"Satisfaction\"/>\n"
            + "          <rows jcr:primaryType=\"fcnt:definitionOptionsTranslatable\"><j:translation_en jcr:primaryType=\"jnt:translation\" jsonValue=\"" + SATISFACTION_ROWS + "\"/></rows>\n"
            + "          <columns jcr:primaryType=\"fcnt:definitionOptionsTranslatable\"><j:translation_en jcr:primaryType=\"jnt:translation\" jsonValue=\"" + SATISFACTION_COLUMNS + "\"/></columns>\n"
            + "          <validations jcr:primaryType=\"fcnt:validationRules\"><rows jcr:primaryType=\"fcnt:matrixRequiredRowsValidation\"><message jcr:primaryType=\"fcnt:definitionOptionsTranslatable\"><j:translation_en jcr:primaryType=\"jnt:translation\" jsonValue=\"Answer every line\"/></message></rows></validations>\n"
            + "        </satisfaction>\n"
            + "        <your-opinion-end jcr:primaryType=\"fcnt:fieldsetEndDefinition\"/>\n"
            // a fieldset whose matrix the start of the next fieldset ends
            + "        <ratings-group jcr:primaryType=\"fcnt:fieldsetStartDefinition\"><j:translation_en jcr:primaryType=\"jnt:translation\" jcr:title=\"Ratings\"/></ratings-group>\n"
            + "        <ratings jcr:primaryType=\"fcnt:matrixRadiosDefinition\" jcr:uuid=\"a0000000-0000-0000-0000-000000000010\" choiceField=\"rows,columns\"><j:translation_en jcr:primaryType=\"jnt:translation\" jcr:title=\"Ratings\"/>\n"
            + "          <rows jcr:primaryType=\"fcnt:definitionOptionsTranslatable\"><j:translation_en jcr:primaryType=\"jnt:translation\" jsonValue=\"[{&quot;key&quot;:&quot;x1&quot;,&quot;value&quot;:&quot;Speed&quot;}]\"/></rows>\n"
            + "          <columns jcr:primaryType=\"fcnt:definitionOptionsTranslatable\"><j:translation_en jcr:primaryType=\"jnt:translation\" jsonValue=\"" + SATISFACTION_COLUMNS + "\"/></columns>\n"
            + "        </ratings>\n"
            + "        <last-words jcr:primaryType=\"fcnt:fieldsetStartDefinition\"><j:translation_en jcr:primaryType=\"jnt:translation\" jcr:title=\"Last words\"/></last-words>\n"
            + "        <comment jcr:primaryType=\"fcnt:inputDefinition\" jcr:uuid=\"c0000000-0000-0000-0000-000000000011\"><j:translation_en jcr:primaryType=\"jnt:translation\" jcr:title=\"Comment\"/></comment>\n"
            + "        <last-words-end jcr:primaryType=\"fcnt:fieldsetEndDefinition\"/>\n"
            // a matrix whose rows are all hidden falls back on a text area: a plain field, the fieldset stays whole
            + "        <contact jcr:primaryType=\"fcnt:fieldsetStartDefinition\"><j:translation_en jcr:primaryType=\"jnt:translation\" jcr:title=\"Contact\"/></contact>\n"
            + "        <zip jcr:primaryType=\"fcnt:inputDefinition\" jcr:uuid=\"c0000000-0000-0000-0000-000000000012\"><j:translation_en jcr:primaryType=\"jnt:translation\" jcr:title=\"Zip\"/></zip>\n"
            + "        <notes jcr:primaryType=\"fcnt:matrixRadiosDefinition\" jcr:uuid=\"a0000000-0000-0000-0000-000000000013\" choiceField=\"rows,columns\"><j:translation_en jcr:primaryType=\"jnt:translation\" jcr:title=\"Notes\"/>\n"
            + "          <rows jcr:primaryType=\"fcnt:definitionOptionsTranslatable\"><j:translation_en jcr:primaryType=\"jnt:translation\" jsonValue=\"[{&quot;key&quot;:&quot;n1&quot;,&quot;value&quot;:&quot;Hidden&quot;,&quot;visible&quot;:false}]\"/></rows>\n"
            + "          <columns jcr:primaryType=\"fcnt:definitionOptionsTranslatable\"><j:translation_en jcr:primaryType=\"jnt:translation\" jsonValue=\"" + SATISFACTION_COLUMNS + "\"/></columns>\n"
            + "        </notes>\n"
            + "        <town jcr:primaryType=\"fcnt:inputDefinition\" jcr:uuid=\"c0000000-0000-0000-0000-000000000014\"><j:translation_en jcr:primaryType=\"jnt:translation\" jcr:title=\"Town\"/></town>\n"
            + "        <contact-end jcr:primaryType=\"fcnt:fieldsetEndDefinition\"/>\n"
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

    static FormsExportReader reader() throws Exception {
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
        // a choice hidden in Forms, which its visitors no longer saw, is no option of the field
        assertEquals(List.of(FormsChoices.option("Very good", "Very good"), FormsChoices.option("Fair", "Fair")),
                form.fieldBySourceName("colours").options().get("en"));
    }

    @Test
    void aPasswordAnswerIsDroppedEvenThoughNoFieldWasCreated() throws Exception {
        FormsExport export = reader().readStructure();
        FormsForm source = export.forms().get("survey");
        FormsResults results = export.results().get("survey");
        ImportedForm form = new FormsFormConverter(t -> true, s -> true).convert(source, results);
        assertEquals(List.of("your-e-mail", "colours", "nickname", "delivery", "city", "age", "the-price", "the-service", "speed", "comment", "zip", "notes", "town"),
                form.fields().map(ImportedField::name).toList());
        List<FormsSubmission> submissions = new ArrayList<>();
        reader().readSubmissions(submissions::add);

        ImportedSubmission converted = new FormsSubmissionConverter(form, source, results).convert(submissions.get(0));
        assertFalse(converted.values().containsKey("password_0_1"));
        assertTrue(converted.values().values().stream().flatMap(List::stream).noneMatch(FormsValues.PASSWORD_PLACEHOLDER::equals));
        assertEquals(1, converted.report().size());
        assertEquals("submission.passwordDropped", converted.report().get(0).code());
        // no field was created for it: the note names it by the title of its Forms definition
        assertEquals("Password", converted.report().get(0).param("field"));
        assertEquals(ImportNote.Level.ACTION, converted.report().get(0).level());
    }

    /** The fieldsets of the form, in order: the ones split around a matrix, the matrices, the one opened inside another. */
    private static List<ImportedContainer> fieldsetsOf(ImportedForm form) {
        return form.elements().stream().filter(ImportedContainer.class::isInstance).map(ImportedContainer.class::cast).toList();
    }

    @Test
    void aMatrixSplitsTheFieldsetAroundItAndAFieldsetStartEndsTheOpenOne() throws Exception {
        FormsExport export = reader().readStructure();
        FormsForm source = export.forms().get("survey");
        ImportedForm form = new FormsFormConverter(t -> true, s -> true).convert(source, export.results().get("survey"));

        // a single-step form keeps its elements at the top. A matrix never goes under a fieldset, which a fieldset
        // cannot hold: the fieldset around the checkboxes matrix is split in two around it, and the one holding the
        // radios matrix alone is not written. A fieldset start while one is open ends it, as Forms does: the fieldset
        // opened inside the first one stands beside its second half, not under it. A fieldset whose matrix the
        // start of the next one ends is not written either, and the matrix takes the name it gave back.
        assertEquals(List.of("your-e-mail", "colours", "about-you", "extras", "about-you-1", "more", "satisfaction", "ratings", "last-words", "contact"),
                form.elements().stream().map(ImportedElement::name).toList());
        // a matrix that falls back on its text area is a field like another: the fieldset around it is not split
        ImportedContainer contact = fieldsetsOf(form).get(fieldsetsOf(form).size() - 1);
        assertEquals(List.of("zip", "notes", "town"), contact.children().stream().map(ImportedElement::name).toList());
        assertEquals(FormsFieldTypes.TEXTAREA, form.fieldBySourceName("notes").nodeType());
        // the title of a fieldset that held a matrix alone is lost, and said
        List<ImportNote> lost = form.report().stream().filter(note -> note.code().equals("form.fieldsetAroundMatrix")).toList();
        assertEquals(List.of("Your opinion/Satisfaction", "Ratings/Ratings"),
                lost.stream().map(note -> note.param("fieldset") + "/" + note.param("matrix")).toList());
        List<ImportedContainer> fieldsets = fieldsetsOf(form);
        ImportedContainer aboutYou = fieldsets.get(0);
        ImportedContainer aboutYouAgain = fieldsets.get(2);
        assertEquals(Map.of("en", "About you"), aboutYou.titles());
        assertEquals(aboutYou.titles(), aboutYouAgain.titles());
        assertEquals(List.of("nickname"), aboutYou.children().stream().map(ImportedElement::name).toList());
        assertEquals(List.of("city"), aboutYouAgain.children().stream().map(ImportedElement::name).toList());
        assertTrue(aboutYou.view() == null && aboutYouAgain.view() == null);
        assertEquals(List.of("age"), fieldsets.get(3).children().stream().map(ImportedElement::name).toList());
        assertEquals(Map.of("en", "More"), fieldsets.get(3).titles());
    }

    @Test
    void aMatrixBecomesAFieldsetOfOneFieldPerRowRenderedAsAMatrix() throws Exception {
        FormsExport export = reader().readStructure();
        FormsForm source = export.forms().get("survey");
        ImportedForm form = new FormsFormConverter(t -> true, s -> true).convert(source, export.results().get("survey"));
        List<ImportedContainer> fieldsets = fieldsetsOf(form);

        ImportedContainer satisfaction = fieldsets.get(4);
        assertEquals(ImportedContainer.FIELDSET, satisfaction.nodeType());
        assertEquals(ImportedContainer.MATRIX_VIEW, satisfaction.view());
        assertEquals(Map.of("en", "Satisfaction"), satisfaction.titles());
        // one radio field per visible row, the columns as its options; "every row answered" is the required rule of each
        List<ImportedField> rows = satisfaction.fields().toList();
        assertEquals(List.of("the-price", "the-service"), rows.stream().map(ImportedField::name).toList());
        assertTrue(rows.stream().allMatch(row -> row.nodeType().equals(FormsFieldTypes.RADIO)));
        assertTrue(rows.stream().allMatch(ImportedField::required));
        assertTrue(rows.stream().allMatch(row -> Map.of("en", "Answer every line").equals(row.i18nProperties().get(FormsFieldTypes.MSG_VALUE_MISSING))));
        assertEquals(List.of("field.matrixRow"), rows.get(0).report().stream().map(ImportNote::code).toList());
        assertEquals(Map.of("en", "The service"), rows.get(1).titles());
        assertEquals(List.of(FormsChoices.option("poor", "Poor"), FormsChoices.option("good", "Good")), rows.get(1).options().get("en"));
        // the row remembers the matrix and its key, as Forms names the inputs of the row
        assertEquals(SATISFACTION_ID + ".q2", rows.get(1).sourceId());
        assertEquals("satisfaction.q2", rows.get(1).sourceName());
        assertEquals("fcnt:matrixRadiosDefinition", rows.get(1).sourceType());
        assertEquals(List.of("field.matrixRow"), rows.get(1).report().stream().map(ImportNote::code).toList());
        assertEquals("matrixRadios", rows.get(1).report().get(0).param("kind"));
        assertEquals(ImportNote.Level.INFO, rows.get(1).report().get(0).level());

        ImportedContainer extras = fieldsets.get(1);
        assertEquals(ImportedContainer.MATRIX_VIEW, extras.view());
        ImportedField delivery = extras.fields().findFirst().orElseThrow();
        assertEquals(FormsFieldTypes.CHECKBOX, delivery.nodeType());
        assertEquals(List.of(FormsChoices.option("fast", "Fast"), FormsChoices.option("cheap", "Cheap")), delivery.options().get("en"));
        // "at least one row answered" has no equivalent: no row required, said once, on the first row
        assertFalse(delivery.required());
        assertEquals(List.of("field.matrixRow", "field.matrixAnyRow"), delivery.report().stream().map(ImportNote::code).toList());
        // the help text of the matrix is the fieldset's; a matrix without one leaves the fieldset without
        assertEquals(Map.of("helpText", Map.of("en", "Tick all that apply")), extras.i18nProperties());
        assertEquals(Map.of(), satisfaction.i18nProperties());
    }

    @Test
    void theAnswersOfAMatrixLandUnderTheFieldOfEachRow() throws Exception {
        FormsExport export = reader().readStructure();
        FormsForm source = export.forms().get("survey");
        FormsResults results = export.results().get("survey");
        ImportedForm form = new FormsFormConverter(t -> true, s -> true).convert(source, results);
        List<FormsSubmission> submissions = new ArrayList<>();
        reader().readSubmissions(submissions::add);

        ImportedSubmission created = new FormsSubmissionConverter(form, source, results).convert(submissions.get(0));
        assertEquals(List.of("good"), created.values().get("the-price"));
        assertEquals(List.of("poor"), created.values().get("the-service"));
        assertEquals(List.of("fast", "cheap"), created.values().get("delivery"));
        assertFalse(created.values().containsKey("satisfaction"));
        assertFalse(created.values().containsKey("extras"));

        // a found form: the field that remembers the row, under the name it has now
        Map<String, String> renamed = Map.of(SATISFACTION_ID + ".q1", "price", "satisfaction.q2", "service", EXTRAS_ID + ".r1", "delivery");
        ImportedSubmission found = new FormsSubmissionConverter(form, source, results, renamed).convert(submissions.get(0));
        assertEquals(List.of("good"), found.values().get("price"));
        assertEquals(List.of("poor"), found.values().get("service"));
        assertEquals(List.of("fast", "cheap"), found.values().get("delivery"));

        // a form imported before the rows had their fields holds one text field for the matrix: the rows stay its lines
        Map<String, String> earlier = Map.of(SATISFACTION_ID, "satisfaction", EXTRAS_ID, "extras");
        ImportedSubmission asLines = new FormsSubmissionConverter(form, source, results, earlier).convert(submissions.get(0));
        assertEquals(List.of("q1: good\nq2: poor"), asLines.values().get("satisfaction"));
        assertEquals(List.of("r1: fast, cheap"), asLines.values().get("extras"));
        assertFalse(asLines.values().containsKey("the-price"));

        // a row hidden since the visitor answered has no field: its answer stays a line under the Forms name, and is said
        ImportedSubmission hiddenRow = new FormsSubmissionConverter(form, source, results).convert(submissions.get(1));
        assertEquals(List.of("good"), hiddenRow.values().get("the-price"));
        assertEquals(List.of("good"), hiddenRow.values().get("the-service"));
        assertEquals(List.of("q3: poor"), hiddenRow.values().get("satisfaction"));
        assertEquals(1, hiddenRow.report().size());
        assertEquals("submission.matrixRowsWithoutField", hiddenRow.report().get(0).code());
        assertEquals("Satisfaction", hiddenRow.report().get(0).param("field"));
        assertEquals("q3: poor", hiddenRow.report().get(0).param("rows"));
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
        assertTrue(form.report().stream().anyMatch(note -> note.code().equals("form.mailCopies")
                && note.param("copies").contains("boss@example.com") && note.param("copies").contains("audit@example.com")), form.report().toString());
    }
}
